# ISC Certification Report

Python tool that exports SailPoint Identity Security Cloud (ISC) certification data to CSV using the [SailPoint Python SDK](https://developer.sailpoint.com/docs/tools/sdk/python).

**Script:** `CertificationReport.py`

**Outputs (per run):**

| File | Description |
|------|-------------|
| `certification_report_{YYYYMMDD_HHMMSS}.csv` | One row per access-review-item |
| `Certification_Summary_{YYYYMMDD_HHMMSS}.csv` | One row per certification (decision-summary counts) |

API reference: [SailPoint ISC API specifications](https://developer.sailpoint.com/docs/api)

---

## Requirements

- Python 3.10+ recommended
- Network access to your ISC tenant
- OAuth API client credentials with rights to read campaigns, certifications, review items, and search account activities

Install dependencies into your environment (standard `site-packages`):

```bash
pip install -r requirements.txt
```

| Package | Version |
|---------|---------|
| `sailpoint` | >= 2.0.0 |
| `requests` | >= 2.28.0 |

---

## Configuration

Copy and edit `config.json`:

```json
{
  "tenant_url": "https://yourorg.api.identitynow.com",
  "client_id": "your-client-id",
  "client_secret": "your-client-secret",
  "output_path": "./reports",
  "log_file": "log.txt",
  "filter": {
    "campaign_name": ["Campaign A", "Campaign B"],
    "campaign_type": "",
    "campaign_status": "",
    "campaign_api_filter": ""
  },
  "account_activity": {
    "enabled": true,
    "max_search_results_per_group": 25
  }
}
```

| Setting | Description |
|---------|-------------|
| `tenant_url` | ISC tenant API base URL |
| `client_id` / `client_secret` | OAuth client credentials |
| `output_path` | Directory for CSV output |
| `log_file` | Log file path; empty → `isc_certification_report.log` in the working directory |
| `filter.campaign_name` | String or list; server filter `(name eq "A" or name eq "B")` |
| `filter.campaign_status` | Server filter `status eq "..."` |
| `filter.campaign_id` | Server filter `id eq "..."` (optional) |
| `filter.campaign_api_filter` | Raw campaigns filter (overrides built-in filters) |
| `filter.campaign_type` | Client-side filter on campaign `type` after fetch |
| `account_activity.enabled` | Look up remediation / SNOW for `REVOKE` items |
| `account_activity.max_search_results_per_group` | Max account-activity search hits per identity/source/time window |

---

## Usage

```bash
python CertificationReport.py
python CertificationReport.py --config path/to/config.json
```

### Console output

The terminal shows **step status only** (`RUNNING`, `OK`, `WARN`, `ERROR`). If a step runs longer than **2 minutes**, a heartbeat is printed every 2 minutes:

```
[3/5] Build unified review rows
      [14:03:10] status: RUNNING
      [14:05:10] status: RUNNING - Build unified review rows (still running...)
      [14:08:45] status: OK - report data built
```

### Log file

Detailed progress, API requests, and full API response bodies are written to the log file only.

---

## API flow

```
GET  campaigns (detail=FULL, optional filters)
  └── GET  certifications?filters=campaign.id eq "{id}"
        ├── GET  certifications/{id}/access-review-items
        │     └── (REVOKE items) POST search (accountactivities)
        └── GET  certifications/{id}/decision-summary  → Summary CSV
```

Remediation and ServiceNow data come from **account activity search** only (no work-item APIs).

### Account activity search (REVOKE items)

```
action:"Certification"
AND recipient.id:"{identitySummary.identityId}"
AND created:[{lowerBound} TO now]
AND sources:"{sourceName}"
```

| Filter part | Source |
|-------------|--------|
| `recipient.id` | `access-review-item.identitySummary.identityId` |
| `created` lower bound | `accessSummary.modified`, or `certification.created` if modified is empty |
| `sources` | See **SourceName** resolution below |

---

## Report 1: `certification_report_{timestamp}.csv`

One row per **access-review-item**.

### Campaign columns

| Column | API / JSON path |
|--------|-----------------|
| CampaignName | `GET campaigns` → `name` |
| CampaignType | `type` |
| CampaignStatus | `status` |
| CampaignStartDate | `created` |
| CampaignDeadline | `deadline` |
| CampaignModified | `modified` |

### Certification columns

| Column | API / JSON path |
|--------|-----------------|
| CertificationName | `GET certifications` → `name` |
| CertificationDue | `due` |
| CertificationCompleted | `completed` |
| CertificationSigned | `signed` |
| CertifierName | `reviewer.name` |
| CertifierEmail | `reviewer.email` |
| DecisionsMade | `decisionsMade` |
| DecisionsTotal | `decisionsTotal` |

### Review item columns

| Column | API / JSON path |
|--------|-----------------|
| ReviewItemCompleted | `GET access-review-items` → `completed` |
| Decision | `decision` (falls back to `recommendation.recommendation`) |
| Comments | `comments` |
| Recommendation | `recommendation.recommendation` |
| IdentityName | `identitySummary.name` |
| AccessName | `accessSummary.access.name` (falls back to entitlement attribute value) |
| AccessType | `accessSummary.access.type` |
| EntitlementAttribute | `accessSummary.entitlement.attributeName` |
| EntitlementValue | `accessSummary.entitlement.attributeValue` |

### SourceName

| `accessSummary.access.type` | Lookup |
|----------------------------|--------|
| `ENTITLEMENT` | `accessSummary.entitlement.sourceName` |
| `ACCESS_PROFILE` | `accessSummary.accessProfile.entitlements[].sourceName` |

Fallback: parse certification `name` (text after `for `, e.g. *Source Owner Access Review for MySource*).

### Remediation columns (REVOKE items only)

Populated when a matching account activity is found via search.

| Column | API / JSON path |
|--------|-----------------|
| RemediationStatus | Account activity `status`, or `Not Required` / `Remediation Not Started` |
| RemediationWorkItemState | `stage` |
| RemediationCreated | `created` |
| RemediationCompleted | *(not mapped)* |
| RemediationErrors | `errors[]` (joined with `\|`) |

### SNOW columns (REVOKE items only)

| Column | API / JSON path |
|--------|-----------------|
| SNOWTicketId | `accountRequests[].result.ticketId` |
| ProvisioningTarget | `accountRequests[].provisioningTarget.name` |

---

## Report 2: `Certification_Summary_{timestamp}.csv`

One row per **certification**. Counts from `GET /certifications/{id}/decision-summary`.

### Identity columns

| Column | API / JSON path |
|--------|-----------------|
| CampaignName | Campaign `name` |
| CertificationName | Certification `name` |
| CertificationCreated | Certification `created` |
| CertificationDue | Certification `due` |
| CertifierName | Certification `reviewer.name` |

### Decision counts

Grouped as **Total → Made → Approved → Revoked** for each access type.

| Entitlement | Access profile | Role | Account |
|-------------|----------------|------|---------|
| EntitlementDecisionsTotal | AccessProfileDecisionsTotal | RoleDecisionsTotal | AccountDecisionsTotal |
| EntitlementDecisionsMade | AccessProfileDecisionsMade | RoleDecisionsMade | AccountDecisionsMade |
| EntitlementsApproved | AccessProfilesApproved | RolesApproved | AccountsApproved |
| EntitlementsRevoked | AccessProfilesRevoked | RolesRevoked | AccountsRevoked |

All count fields map directly to the same field names in the decision-summary API response.

---

## Project layout

| File | Purpose |
|------|---------|
| `CertificationReport.py` | Main report script |
| `report_logging.py` | File logging and step progress |
| `config.json` | Tenant credentials and filters |
| `requirements.txt` | Python dependencies |
| `COLUMN_MAPPING.md` | Extended column / API notes |

Legacy scripts (`CertReport.py`, `CertUnifiedReport.py`) are older variants and are not used by the main workflow above.
