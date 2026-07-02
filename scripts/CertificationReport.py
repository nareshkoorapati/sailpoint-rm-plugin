#!/usr/bin/env python3
"""
ISC Certification Report (SailPoint Python SDK)

Standalone report using the official SailPoint Python SDK:
https://developer.sailpoint.com/docs/tools/sdk/python

One row per access-review-item with campaign, certification, certifier,
review decision, remediation status, and ServiceNow ticket.

Usage:
    pip install --target lib -r requirements.txt
    python CertificationReport.py --config config.json

Requires:
    pip install sailpoint>=2.0.0
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterator

# Prefer project-local packages (lib/) when system site-packages is incomplete.
# Install once: pip install --target lib -r requirements.txt
_LIB_DIR = Path(__file__).resolve().parent / "lib"
if _LIB_DIR.is_dir():
    sys.path.insert(0, str(_LIB_DIR))

try:
    from sailpoint.certification_campaigns.api.certification_campaigns_api import (
        CertificationCampaignsApi,
    )
    from sailpoint.certification_campaigns.api_client import ApiClient as CampaignsApiClient
    from sailpoint.certification_summaries.api.certification_summaries_api import (
        CertificationSummariesApi,
    )
    from sailpoint.certification_summaries.api_client import (
        ApiClient as CertificationSummariesApiClient,
    )
    from sailpoint.certifications.api.certifications_api import CertificationsApi
    from sailpoint.certifications.api_client import ApiClient as CertificationsApiClient
    from sailpoint.configuration import Configuration
    from sailpoint.paginator import Paginator
    from sailpoint.search.api.search_api import SearchApi
    from sailpoint.search.api_client import ApiClient as SearchApiClient
    from sailpoint.search.models.index import Index
    from sailpoint.search.models.query import Query
    from sailpoint.search.models.search import Search
except ModuleNotFoundError as exc:
    print(
        "SailPoint Python SDK import failed.\n"
        f"  {exc}\n\n"
        "Install dependencies into the project lib folder:\n"
        "  pip install --target lib -r requirements.txt\n"
        "Then run:\n"
        "  python CertificationReport.py --config config.json",
        file=sys.stderr,
    )
    raise SystemExit(1) from exc

from report_logging import (
    StepReporter,
    StepStatus,
    create_report_logger,
    get_active_logger,
)


# ---------------------------------------------------------------------------
# Progress reporting (StepReporter imported from report_logging)
# ---------------------------------------------------------------------------


def load_config(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise FileNotFoundError(f"Config file not found: {path}")

    with path.open(encoding="utf-8") as handle:
        config = json.load(handle)

    required = ("tenant_url", "client_id", "client_secret", "output_path")
    missing = [key for key in required if not config.get(key)]
    if missing:
        raise ValueError(
            f"Missing required config value(s) in {path}: {', '.join(missing)}"
        )

    config.setdefault("filter", {})
    config.setdefault("log_file", "")
    config.setdefault("log_file_max_mb", 0)
    config.setdefault(
        "account_activity",
        {
            "enabled": True,
            "max_search_results_per_group": 25,
        },
    )
    return config


def build_sdk_configuration(config: dict[str, Any]) -> Configuration:
    """
    Initialize SDK Configuration from report config.json.

    Uses environment variables so the SDK does not require its own
    config.json format (ClientId / ClientSecret / BaseURL).
    See: https://developer.sailpoint.com/docs/tools/sdk/python
    """
    os.environ["SAIL_BASE_URL"] = config["tenant_url"].rstrip("/")
    os.environ["SAIL_CLIENT_ID"] = config["client_id"]
    os.environ["SAIL_CLIENT_SECRET"] = config["client_secret"]
    return Configuration()


class SdkApis:
    """Holds SDK API client instances for one authenticated session."""

    def __init__(self, configuration: Configuration) -> None:
        self._configuration = configuration
        self._campaigns_client = CampaignsApiClient(configuration)
        self._certifications_client = CertificationsApiClient(configuration)
        self._summaries_client = CertificationSummariesApiClient(configuration)
        self._search_client = SearchApiClient(configuration)

        self.campaigns = CertificationCampaignsApi(self._campaigns_client)
        self.certifications = CertificationsApi(self._certifications_client)
        self.summaries = CertificationSummariesApi(self._summaries_client)
        self.search = SearchApi(self._search_client)

    def close(self) -> None:
        for client in (
            self._campaigns_client,
            self._certifications_client,
            self._summaries_client,
            self._search_client,
        ):
            if hasattr(client, "close"):
                client.close()


@contextmanager
def sdk_session(config: dict[str, Any]) -> Iterator[SdkApis]:
    apis = SdkApis(build_sdk_configuration(config))
    try:
        yield apis
    finally:
        apis.close()


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _unwrap_sdk(obj: Any) -> Any:
    """Unwrap SailPoint SDK AnyOf wrappers (actual_instance)."""
    current = obj
    while getattr(current, "actual_instance", None) is not None:
        current = current.actual_instance
    return current


def _field(obj: Any, *names: str, default: Any = None) -> Any:
    obj = _unwrap_sdk(obj)
    if obj is None:
        return default
    for name in names:
        if isinstance(obj, dict):
            value = obj.get(name)
        else:
            value = getattr(obj, name, None)
        if value is not None:
            return value
    return default


def _scalar_field(obj: Any, *names: str) -> str:
    """Read a scalar from SDK models, dicts, or model_dump (by alias)."""
    value = _field(obj, *names)
    if value not in (None, ""):
        return _val(value)
    unwrapped = _unwrap_sdk(obj)
    if unwrapped is None:
        return ""
    if hasattr(unwrapped, "model_dump"):
        try:
            data = unwrapped.model_dump(by_alias=True)
            for name in names:
                candidate = data.get(name)
                if candidate not in (None, ""):
                    return _val(candidate)
        except Exception:
            pass
    return ""


def _format_csv_dt(value: datetime) -> str:
    utc = value.astimezone(timezone.utc)
    ms = int(utc.microsecond / 1000)
    return utc.strftime("%Y-%m-%dT%H:%M:%S") + f".{ms:03d}Z"


def _val(value: Any, default: str = "") -> str:
    if value is None:
        return default
    if isinstance(value, datetime):
        return _format_csv_dt(value)
    if isinstance(value, bool):
        return str(value)
    if hasattr(value, "value"):
        return str(value.value)
    return str(value)


def extract_review_decision(item: Any) -> str:
    """Read APPROVE/REVOKE from a review item (SDK model or REST dict)."""
    if isinstance(item, dict):
        decision = item.get("decision")
        if not decision:
            rec = item.get("recommendation") or {}
            if isinstance(rec, dict):
                decision = rec.get("recommendation")
    else:
        obj = _unwrap_sdk(item)
        decision = getattr(obj, "decision", None)
        if decision is None and hasattr(obj, "model_dump"):
            decision = obj.model_dump().get("decision")
    if decision is None:
        return ""
    if isinstance(decision, str):
        return decision
    return _val(decision)


def _escape_search(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def write_csv(rows: list[dict], path: Path, reporter: StepReporter) -> None:
    if not rows:
        reporter.detail(f"(no rows to write for {path.name})")
        return
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    reporter.detail(f"Written: {path} ({len(rows)} rows)")


def _escape_filter_value(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def _normalize_campaign_names(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        return [str(name).strip() for name in value if str(name).strip()]
    text = str(value).strip()
    return [text] if text else []


def _campaign_names_from_filters(filters: dict[str, Any]) -> list[str]:
    return _normalize_campaign_names(filters.get("campaign_name"))


def _campaign_name_api_clause(names: list[str]) -> str:
    if not names:
        return ""
    if len(names) == 1:
        return f'name eq "{_escape_filter_value(names[0])}"'
    parts = [f'name eq "{_escape_filter_value(name)}"' for name in names]
    return "(" + " or ".join(parts) + ")"


def build_campaign_api_filter(filters: dict[str, Any]) -> str:
    """Build ISC campaigns API filter from config (server-side where supported)."""
    api_filter = (filters.get("campaign_api_filter") or "").strip()
    if api_filter:
        return api_filter

    clauses: list[str] = []
    campaign_id = (filters.get("campaign_id") or "").strip()
    campaign_names = _campaign_names_from_filters(filters)
    campaign_status = (filters.get("campaign_status") or "").strip()

    if campaign_id:
        clauses.append(f'id eq "{_escape_filter_value(campaign_id)}"')
    name_clause = _campaign_name_api_clause(campaign_names)
    if name_clause:
        clauses.append(name_clause)
    if campaign_status:
        clauses.append(f'status eq "{_escape_filter_value(campaign_status)}"')

    return " and ".join(clauses)


def _campaign_filtered_on_api(filters: dict[str, Any]) -> bool:
    if (filters.get("campaign_api_filter") or "").strip():
        return True
    if _campaign_names_from_filters(filters):
        return True
    return any(
        (filters.get(key) or "").strip()
        for key in ("campaign_id", "campaign_status")
    )


def filter_campaigns(campaigns: list, filters: dict[str, Any]) -> list:
    type_f = (filters.get("campaign_type") or "").strip().upper()
    campaign_names = _campaign_names_from_filters(filters)
    name_lookup = {name.lower() for name in campaign_names}

    result = campaigns
    if type_f:
        result = [c for c in result if _val(getattr(c, "type", "")).upper() == type_f]

    if _campaign_filtered_on_api(filters):
        return result

    status_f = (filters.get("campaign_status") or "").strip().upper()
    id_f = (filters.get("campaign_id") or "").strip()

    if name_lookup:
        result = [
            c for c in result if _val(getattr(c, "name", "")).lower() in name_lookup
        ]
    if status_f:
        result = [
            c for c in result if _val(getattr(c, "status", "")).upper() == status_f
        ]
    if id_f:
        result = [c for c in result if getattr(c, "id", "") == id_f]
    return result


def build_campaign_api_params(filters: dict[str, Any]) -> dict[str, Any]:
    params: dict[str, Any] = {"detail": "FULL"}
    api_filter = build_campaign_api_filter(filters)
    if api_filter:
        params["filters"] = api_filter
    return params


def _sdk_to_jsonable(obj: Any) -> Any:
    obj = _unwrap_sdk(obj)
    if isinstance(obj, dict):
        return obj
    if isinstance(obj, list):
        return [_sdk_to_jsonable(item) for item in obj]
    if hasattr(obj, "model_dump"):
        return obj.model_dump()
    if hasattr(obj, "to_dict"):
        return obj.to_dict()
    return str(obj)


def paginate(api_method, **kwargs) -> list:
    kwargs.setdefault("limit", 250)
    kwargs.setdefault("count", True)
    logger = get_active_logger()
    method_name = getattr(api_method, "__name__", str(api_method))
    if logger:
        logger.log_http(
            "SDK",
            method_name,
            request_body=kwargs,
        )
    results = Paginator.paginate(api_method, 0, **kwargs)
    unwrapped = [_unwrap_sdk(item) for item in results]
    if logger:
        logger.log_http(
            "SDK",
            method_name,
            status_code=200,
            result_count=len(unwrapped),
            response_body=[_sdk_to_jsonable(item) for item in unwrapped],
        )
    return unwrapped


def _entitlements_list(container: Any) -> list[Any]:
    ents = _field(container, "entitlements")
    if not ents:
        return []
    if isinstance(ents, list):
        return [ent for ent in ents if ent is not None]
    return []


def _first_scalar_from_entitlements(
    container: Any,
    *names: str,
) -> str:
    for ent in _entitlements_list(container):
        value = _scalar_field(ent, *names)
        if value:
            return value
    return ""


def resolve_source_from_access_summary(summary: Any) -> tuple[str, str]:
    """Resolve connector source name and provenance from accessSummary."""
    if not summary:
        return "", "(not found — sources filter omitted)"

    access = _field(summary, "access")
    access_type = _scalar_field(access, "type").upper()
    summary_ent = _field(summary, "entitlement")
    summary_ap = _field(summary, "access_profile", "accessProfile")

    if access_type == "ENTITLEMENT":
        source = _scalar_field(summary_ent, "source_name", "sourceName")
        if source:
            return (
                normalize_source_name(source),
                "access-review-item.accessSummary.entitlement.sourceName",
            )

    if access_type == "ACCESS_PROFILE":
        source = _first_scalar_from_entitlements(summary_ap, "source_name", "sourceName")
        if source:
            return (
                normalize_source_name(source),
                "access-review-item.accessSummary.accessProfile.entitlements[].sourceName",
            )
        ap_source = _scalar_field(summary_ap, "source_name", "sourceName")
        if ap_source:
            return (
                normalize_source_name(ap_source),
                "access-review-item.accessSummary.accessProfile.sourceName",
            )

    source = _scalar_field(summary_ent, "source_name", "sourceName")
    if source:
        return (
            normalize_source_name(source),
            "access-review-item.accessSummary.entitlement.sourceName",
        )

    source = _first_scalar_from_entitlements(summary_ap, "source_name", "sourceName")
    if source:
        return (
            normalize_source_name(source),
            "access-review-item.accessSummary.accessProfile.entitlements[].sourceName",
        )

    ap_source = _scalar_field(summary_ap, "source_name", "sourceName")
    if ap_source:
        return (
            normalize_source_name(ap_source),
            "access-review-item.accessSummary.accessProfile.sourceName",
        )

    return "", "(not found — sources filter omitted)"


def extract_entitlement_from_item(item) -> tuple[str, str, str]:
    summary = _field(item, "access_summary", "accessSummary")
    if not summary:
        return "", "", ""
    access = _field(summary, "access")
    access_type = _scalar_field(access, "type").upper()
    ent = _field(summary, "entitlement")
    ap = _field(summary, "access_profile", "accessProfile")

    attr_name = ""
    attr_value = ""
    source = ""

    if access_type == "ENTITLEMENT" and ent:
        attr_name = _scalar_field(ent, "attribute_name", "attributeName")
        attr_value = _scalar_field(ent, "attribute_value", "attributeValue")
        source = _scalar_field(ent, "source_name", "sourceName")
    elif access_type == "ACCESS_PROFILE" and ap:
        profile_ents = _entitlements_list(ap)
        if profile_ents:
            first = profile_ents[0]
            attr_name = _scalar_field(first, "attribute_name", "attributeName")
            attr_value = _scalar_field(first, "attribute_value", "attributeValue")
        source = _first_scalar_from_entitlements(ap, "source_name", "sourceName")
        if not source:
            source = _scalar_field(ap, "source_name", "sourceName")
    else:
        attr_name = _scalar_field(ent, "attribute_name", "attributeName")
        attr_value = _scalar_field(ent, "attribute_value", "attributeValue")
        source = _scalar_field(ent, "source_name", "sourceName")
        if not source:
            source = _first_scalar_from_entitlements(ap, "source_name", "sourceName")
        if not source:
            source = _scalar_field(ap, "source_name", "sourceName")

    access_name = _scalar_field(access, "name")
    if not attr_value and access_name:
        attr_value = access_name
    return attr_name, attr_value, normalize_source_name(source)


def normalize_source_name(source: str) -> str:
    value = (source or "").strip()
    if value.lower().endswith(" [source]"):
        return value[:-9].strip()
    return value


def infer_source_from_cert_name(cert_name: str) -> str:
    match = re.search(r"for\s+([^'\"]+)", cert_name, re.IGNORECASE)
    if not match:
        return ""
    return normalize_source_name(match.group(1).strip())


def resolve_search_created_lower_bound(item: Any, cert: Any) -> tuple[str, str]:
    summary = _field(item, "access_summary", "accessSummary")
    modified = _scalar_field(summary, "modified")
    if modified:
        return modified, "access-review-item.accessSummary.modified"
    cert_created = _scalar_field(cert, "created")
    if cert_created:
        return cert_created, "certification.created"
    return (
        "",
        "access-review-item.accessSummary.modified and certification.created "
        "(both empty)",
    )


def extract_activity_search_filter_context(
    item: Any,
    cert_name: str,
    cert: Any,
) -> dict[str, str]:
    identity = _field(item, "identity_summary", "identitySummary")
    summary = _field(item, "access_summary", "accessSummary")

    identity_id = _scalar_field(identity, "identity_id", "identityId")
    identity_name = _scalar_field(identity, "name")
    created_lower, created_from = resolve_search_created_lower_bound(item, cert)

    source_name, source_from = resolve_source_from_access_summary(summary)
    if not source_name:
        inferred = infer_source_from_cert_name(cert_name)
        if inferred:
            source_name = inferred
            source_from = (
                f'infer_source_from_cert_name(certification.name="{cert_name}")'
            )

    return {
        "identity_id": identity_id,
        "identity_id_from": "access-review-item.identitySummary.identityId",
        "identity_name": identity_name,
        "source_name": normalize_source_name(source_name),
        "source_from": source_from,
        "access_summary_modified": created_lower,
        "access_summary_modified_from": created_from,
        "cert_name": cert_name,
    }


def log_activity_search_filter_inputs(
    reporter: StepReporter,
    ctx: dict[str, str],
    query: str,
) -> None:
    reporter.detail("  account activity search filter inputs:")
    reporter.detail('    action = "Certification" (fixed literal)')
    reporter.detail(
        f'    recipient.id = "{ctx.get("identity_id", "")}" '
        f'<- {ctx.get("identity_id_from", "")}'
    )
    if ctx.get("identity_name"):
        reporter.detail(f"      identity name: {ctx['identity_name']}")
    if ctx.get("access_summary_modified"):
        reporter.detail(
            f'    created lower bound = "{ctx["access_summary_modified"]}" '
            f'<- {ctx.get("access_summary_modified_from", "")}'
        )
    else:
        reporter.detail(
            "    created = (omitted) "
            f'<- {ctx.get("access_summary_modified_from", "")} was empty'
        )
    if ctx.get("source_name"):
        reporter.detail(
            f'    sources = "{ctx["source_name"]}" <- {ctx.get("source_from", "")}'
        )
    else:
        reporter.detail(f'    sources = (omitted) <- {ctx.get("source_from", "")}')
    reporter.detail(f"    assembled query = {query}")
    logger = get_active_logger()
    if logger:
        logger.log(
            "account activity search filter | "
            + json.dumps({**ctx, "query": query}, default=str)
        )


# ---------------------------------------------------------------------------
# Account activity / SNOW
# ---------------------------------------------------------------------------


def _escape_search_datetime_literal(value: str) -> str:
    return value.replace(":", "\\:")


def build_certification_activity_search_query(
    identity_id: str,
    source_name: str,
    access_summary_modified: str,
) -> str:
    clauses = ['action:"Certification"']
    if identity_id:
        clauses.append(f'recipient.id:"{_escape_search(identity_id)}"')
    if access_summary_modified:
        clauses.append(
            f"created:[{_escape_search_datetime_literal(access_summary_modified)} TO now]"
        )
    if source_name:
        clauses.append(
            f'sources:"{_escape_search(normalize_source_name(source_name))}"'
        )
    return " AND ".join(clauses)


def _coerce_text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, list):
        return "|".join(_coerce_text(item) for item in value if item is not None)
    if isinstance(value, dict):
        return json.dumps(value, default=str)
    return str(value)


def _text_lower(value: Any) -> str:
    return _coerce_text(value).lower()


def _iter_attribute_requests(account_request: dict) -> list[dict]:
    if account_request.get("attributeRequests"):
        return list(account_request["attributeRequests"])
    if account_request.get("attributeRequest"):
        return [account_request["attributeRequest"]]
    return []


def extract_snow_ticket(account_request: dict) -> tuple[str, str]:
    result = account_request.get("result")
    if isinstance(result, dict):
        return (
            str(result.get("ticketId") or result.get("ticket_id") or ""),
            str(result.get("status") or ""),
        )
    return "", str(result or "")


def pick_activity_for_review_item(
    activities: list[dict],
    access_name: str,
    entitlement_value: str,
) -> dict | None:
    if not activities:
        return None
    if len(activities) == 1:
        return activities[0]

    access_lower = _text_lower(access_name)
    value_lower = _text_lower(entitlement_value)
    for activity in activities:
        for account_request in activity.get("accountRequests") or []:
            if not isinstance(account_request, dict):
                continue
            for attr in _iter_attribute_requests(account_request):
                if isinstance(attr, dict):
                    raw_value = attr.get("value")
                else:
                    raw_value = _field(attr, "value")
                attr_val = _text_lower(raw_value)
                if value_lower and (value_lower in attr_val or attr_val in value_lower):
                    return activity
                if access_lower and access_lower in attr_val:
                    return activity
    return activities[0]


def remediation_from_account_activity(activity: dict) -> dict[str, str]:
    errors = activity.get("errors") or []
    if isinstance(errors, list):
        error_text = "|".join(str(err) for err in errors if err)
    else:
        error_text = str(errors) if errors else ""

    ticket_ids: list[str] = []
    prov_target = ""
    for account_request in activity.get("accountRequests") or []:
        if not isinstance(account_request, dict):
            continue
        ticket_id, _ticket_status = extract_snow_ticket(account_request)
        if ticket_id and ticket_id not in ticket_ids:
            ticket_ids.append(ticket_id)
        if not prov_target:
            target = account_request.get("provisioningTarget") or {}
            if isinstance(target, dict):
                prov_target = str(target.get("name") or "")
            else:
                prov_target = _coerce_text(_field(target, "name"))

    return {
        "RemediationStatus": str(activity.get("status") or ""),
        "RemediationWorkItemState": str(activity.get("stage") or ""),
        "RemediationCreated": str(activity.get("created") or ""),
        "RemediationCompleted": "",
        "RemediationErrors": error_text,
        "SNOWTicketId": "|".join(ticket_ids),
        "ProvisioningTarget": prov_target,
    }


def empty_remediation_fields(decision: str) -> tuple[dict[str, str], dict[str, str]]:
    remediation = {
        "RemediationStatus": "Not Required"
        if (decision or "").upper() != "REVOKE"
        else "Remediation Not Started",
        "RemediationWorkItemState": "",
        "RemediationCreated": "",
        "RemediationCompleted": "",
        "RemediationErrors": "",
    }
    snow = {
        "SNOWTicketId": "",
        "ProvisioningTarget": "",
    }
    return remediation, snow


def search_account_activities(
    apis: SdkApis,
    query: str,
    max_results: int,
) -> list[dict]:
    search = Search(
        indices=[Index.ACCOUNTACTIVITIES],
        query=Query(query=query),
        sort=["-created"],
        include_nested=True,
    )
    logger = get_active_logger()
    if logger:
        logger.log_http(
            "POST",
            "search_post_v1 (accountactivities)",
            request_body={"query": query, "limit": min(max_results, 250)},
        )
    try:
        results = apis.search.search_post_v1(search, limit=min(max_results, 250))
    except Exception as exc:
        if logger:
            logger.log_http(
                "POST",
                "search_post_v1 (accountactivities)",
                request_body={"query": query},
                error=str(exc),
            )
        raise
    out: list[dict] = []
    for r in results:
        if isinstance(r, dict):
            out.append(r)
        elif hasattr(r, "model_dump"):
            out.append(r.model_dump())
        else:
            out.append(dict(r))
    if logger:
        logger.log_http(
            "POST",
            "search_post_v1 (accountactivities)",
            status_code=200,
            result_count=len(out),
            response_body=out,
        )
    return out


class AccountActivityLookup:
    def __init__(
        self,
        apis: SdkApis,
        config: dict[str, Any],
        reporter: StepReporter,
    ) -> None:
        self.apis = apis
        self.config = config.get("account_activity") or {}
        self.reporter = reporter
        self._cache: dict[tuple[str, str, str], list[dict]] = {}
        self.max_results = int(self.config.get("max_search_results_per_group", 25))

    def search(self, filter_ctx: dict[str, str]) -> list[dict]:
        if not self.config.get("enabled", True):
            return []

        identity_id = filter_ctx.get("identity_id", "")
        source_name = filter_ctx.get("source_name", "")
        access_summary_modified = filter_ctx.get("access_summary_modified", "")

        if not identity_id:
            self.reporter.detail(
                "  account activity search skipped: identitySummary.identityId is empty"
            )
            return []

        key = (identity_id, source_name or "", access_summary_modified or "")
        if key in self._cache:
            self.reporter.detail(
                f"  account activity (cached): recipient.id={identity_id} @ "
                f"{source_name or '?'}"
            )
            return self._cache[key]

        query = build_certification_activity_search_query(
            identity_id, source_name, access_summary_modified
        )
        log_activity_search_filter_inputs(self.reporter, filter_ctx, query)
        try:
            hits = search_account_activities(self.apis, query, self.max_results)
        except Exception as exc:
            self.reporter.detail(f"  [WARN] search failed: {exc}")
            hits = []

        self.reporter.detail(f"  account activity results: {len(hits)}")
        self._cache[key] = hits
        return hits


# ---------------------------------------------------------------------------
# Unified rows
# ---------------------------------------------------------------------------


def build_unified_rows(
    apis: SdkApis,
    campaigns: list,
    activity_lookup: AccountActivityLookup,
    reporter: StepReporter,
) -> list[dict]:
    rows: list[dict] = []

    for campaign in campaigns:
        camp_id = getattr(campaign, "id", "")
        camp_name = _val(getattr(campaign, "name", None))
        reporter.detail(f"Campaign: '{camp_name}'")

        certifications = paginate(
            apis.certifications.list_identity_certifications_v1,
            filters=f'campaign.id eq "{camp_id}"',
        )
        if not certifications:
            reporter.detail("  -> no certifications")
            continue

        for cert in certifications:
            cert_id = getattr(cert, "id", "")
            cert_name = _val(getattr(cert, "name", None))
            reviewer = getattr(cert, "reviewer", None)

            review_items = paginate(
                apis.certifications.list_identity_access_review_items_v1,
                id=cert_id,
            )
            reporter.detail(f"  review items ({cert_name}): {len(review_items)}")

            for item in review_items:
                identity = getattr(item, "identity_summary", None)
                identity_name = _val(getattr(identity, "name", None) if identity else None)
                ent_attr, ent_val, source = extract_entitlement_from_item(item)
                filter_ctx = extract_activity_search_filter_context(item, cert_name, cert)
                if not source:
                    source = filter_ctx["source_name"]

                summary = getattr(item, "access_summary", None)
                access = getattr(summary, "access", None) if summary else None
                access_name = _val(getattr(access, "name", None) if access else None) or ent_val
                access_type = _val(getattr(access, "type", None) if access else None)
                decision = extract_review_decision(item)

                remediation, snow = empty_remediation_fields(decision)

                if (decision or "").upper() == "REVOKE":
                    activities = activity_lookup.search(filter_ctx)
                    activity = pick_activity_for_review_item(
                        activities, access_name, ent_val
                    )
                    if activity:
                        fields = remediation_from_account_activity(activity)
                        remediation = {k: fields[k] for k in remediation if k in fields}
                        snow = {k: fields[k] for k in snow if k in fields}

                rows.append(
                    {
                        "CampaignName": camp_name,
                        "CampaignType": _val(getattr(campaign, "type", None)),
                        "CampaignStatus": _val(getattr(campaign, "status", None)),
                        "CampaignStartDate": _val(getattr(campaign, "created", None)),
                        "CampaignDeadline": _val(getattr(campaign, "deadline", None)),
                        "CampaignModified": _val(getattr(campaign, "modified", None)),
                        "CertificationName": cert_name,
                        "CertificationDue": _val(getattr(cert, "due", None)),
                        "CertificationCompleted": _val(getattr(cert, "completed", None)),
                        "CertificationSigned": _val(getattr(cert, "signed", None)),
                        "CertifierName": _val(
                            getattr(reviewer, "name", None) if reviewer else None
                        ),
                        "CertifierEmail": _val(
                            getattr(reviewer, "email", None) if reviewer else None
                        ),
                        "DecisionsMade": _val(getattr(cert, "decisions_made", None)),
                        "DecisionsTotal": _val(getattr(cert, "decisions_total", None)),
                        "ReviewItemCompleted": _val(getattr(item, "completed", None)),
                        "Decision": decision,
                        "Comments": _val(getattr(item, "comments", None)),
                        "Recommendation": _val(
                            _field(_field(item, "recommendation"), "recommendation")
                        ),
                        "IdentityName": identity_name,
                        "AccessName": access_name,
                        "AccessType": access_type,
                        "SourceName": source,
                        "EntitlementAttribute": ent_attr,
                        "EntitlementValue": ent_val,
                        **remediation,
                        **snow,
                    }
                )
    return rows


# ---------------------------------------------------------------------------
# Certification decision-summary report
# ---------------------------------------------------------------------------

DECISION_SUMMARY_COUNT_FIELDS: list[tuple[str, str, str]] = [
    (
        "EntitlementDecisionsTotal",
        "entitlement_decisions_total",
        "entitlementDecisionsTotal",
    ),
    ("EntitlementDecisionsMade", "entitlement_decisions_made", "entitlementDecisionsMade"),
    ("EntitlementsApproved", "entitlements_approved", "entitlementsApproved"),
    ("EntitlementsRevoked", "entitlements_revoked", "entitlementsRevoked"),
    (
        "AccessProfileDecisionsTotal",
        "access_profile_decisions_total",
        "accessProfileDecisionsTotal",
    ),
    (
        "AccessProfileDecisionsMade",
        "access_profile_decisions_made",
        "accessProfileDecisionsMade",
    ),
    ("AccessProfilesApproved", "access_profiles_approved", "accessProfilesApproved"),
    ("AccessProfilesRevoked", "access_profiles_revoked", "accessProfilesRevoked"),
    ("RoleDecisionsTotal", "role_decisions_total", "roleDecisionsTotal"),
    ("RoleDecisionsMade", "role_decisions_made", "roleDecisionsMade"),
    ("RolesApproved", "roles_approved", "rolesApproved"),
    ("RolesRevoked", "roles_revoked", "rolesRevoked"),
    ("AccountDecisionsTotal", "account_decisions_total", "accountDecisionsTotal"),
    ("AccountDecisionsMade", "account_decisions_made", "accountDecisionsMade"),
    ("AccountsApproved", "accounts_approved", "accountsApproved"),
    ("AccountsRevoked", "accounts_revoked", "accountsRevoked"),
]


def fetch_decision_summary(apis: SdkApis, cert_id: str) -> Any:
    """GET /certifications/{id}/decision-summary"""
    logger = get_active_logger()
    url = f"certifications/{cert_id}/decision-summary"
    if logger:
        logger.log_http("GET", url)
    try:
        result = _unwrap_sdk(apis.summaries.get_identity_decision_summary_v1(cert_id))
        if logger:
            logger.log_http(
                "GET",
                url,
                status_code=200,
                response_body=_sdk_to_jsonable(result),
            )
        return result
    except Exception as exc:
        if logger:
            logger.log_http("GET", url, error=str(exc))
        raise


def build_certification_summary_rows(
    apis: SdkApis,
    campaigns: list,
    reporter: StepReporter,
) -> list[dict]:
    rows: list[dict] = []

    for campaign in campaigns:
        camp_id = getattr(campaign, "id", "")
        camp_name = _val(getattr(campaign, "name", None))
        reporter.detail(f"Campaign: '{camp_name}'")

        certifications = paginate(
            apis.certifications.list_identity_certifications_v1,
            filters=f'campaign.id eq "{camp_id}"',
        )
        if not certifications:
            reporter.detail("  -> no certifications")
            continue

        for cert in certifications:
            cert_id = getattr(cert, "id", "")
            cert_name = _val(getattr(cert, "name", None))
            reviewer = getattr(cert, "reviewer", None)

            row: dict[str, str] = {
                "CampaignName": camp_name,
                "CertificationName": cert_name,
                "CertificationCreated": _val(getattr(cert, "created", None)),
                "CertificationDue": _val(getattr(cert, "due", None)),
                "CertifierName": _val(
                    getattr(reviewer, "name", None) if reviewer else None
                ),
            }

            try:
                summary = fetch_decision_summary(apis, cert_id)
            except Exception as exc:
                reporter.detail(
                    f"  [WARN] decision-summary for '{cert_name}' ({cert_id}): {exc}"
                )
                summary = None

            for column, snake, camel in DECISION_SUMMARY_COUNT_FIELDS:
                row[column] = (
                    _scalar_field(summary, snake, camel) if summary is not None else ""
                )

            rows.append(row)
            reporter.detail(f"  summary row: {cert_name}")

    return rows


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Export ISC certification report via SailPoint Python SDK"
    )
    parser.add_argument("--config", default="config.json")
    args = parser.parse_args()

    print("=== ISC Certification Report (Python SDK) ===\n")

    try:
        config = load_config(Path(args.config))
    except (FileNotFoundError, ValueError, json.JSONDecodeError) as exc:
        print(f"Configuration error: {exc}", file=sys.stderr)
        return 1

    logger = create_report_logger(
        config,
        "CertificationReport.py",
        base_dir=Path.cwd(),
    )
    reporter = StepReporter(total_steps=5, logger=logger)

    reporter.begin("Load configuration")
    out_dir = Path(config["output_path"])
    filters = config.get("filter") or {}
    reporter.detail(f"Tenant      : {config['tenant_url'].rstrip('/')}")
    reporter.detail(f"Output path : {out_dir}")
    reporter.detail(f"Log file    : {logger.path}")
    reporter.end(StepStatus.OK, "configuration loaded")

    out_dir.mkdir(parents=True, exist_ok=True)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")

    try:
        with sdk_session(config) as apis:
            reporter.begin("Fetch certification campaigns (SDK)")
            try:
                campaigns = paginate(
                    apis.campaigns.get_active_campaigns_v1,
                    **build_campaign_api_params(filters),
                )
                campaigns = filter_campaigns(campaigns, filters)
                if not campaigns:
                    reporter.end(StepStatus.WARN, "no matching campaigns")
                    return 0
                reporter.end(StepStatus.OK, f"{len(campaigns)} campaign(s)")
            except Exception as exc:
                reporter.end(StepStatus.ERROR, str(exc))
                return 1

            reporter.begin("Build unified review rows")
            try:
                activity_lookup = AccountActivityLookup(apis, config, reporter)
                rows = build_unified_rows(
                    apis,
                    campaigns,
                    activity_lookup,
                    reporter,
                )
                reporter.detail(f"Unified rows: {len(rows)}")
                reporter.end(StepStatus.OK, "report data built")
            except Exception as exc:
                reporter.end(StepStatus.ERROR, str(exc))
                return 1

            reporter.begin("Build certification summary rows")
            try:
                summary_rows = build_certification_summary_rows(
                    apis,
                    campaigns,
                    reporter,
                )
                reporter.detail(f"Summary rows: {len(summary_rows)}")
                reporter.end(StepStatus.OK, "summary data built")
            except Exception as exc:
                reporter.end(StepStatus.ERROR, str(exc))
                return 1

            reporter.begin("Export CSV reports")
            write_csv(
                rows,
                out_dir / f"certification_report_{ts}.csv",
                reporter,
            )
            write_csv(
                summary_rows,
                out_dir / f"Certification_Summary_{ts}.csv",
                reporter,
            )
            reporter.end(StepStatus.OK, f"written to {out_dir}")

    except Exception as exc:
        active_logger = get_active_logger()
        if active_logger:
            active_logger.log(f"SDK session error: {exc}")
        print(f"SDK session error: {exc}", file=sys.stderr)
        return 1

    print("=== Report Complete ===")
    print(f"Output: certification_report_{ts}.csv")
    print(f"Output: Certification_Summary_{ts}.csv")
    active_logger = get_active_logger()
    if active_logger:
        active_logger.log("=== Report complete ===")
    return 0


if __name__ == "__main__":
    sys.exit(main())
