"""File logging for ISC certification report scripts."""

from __future__ import annotations

import json
import threading
from datetime import datetime
from enum import Enum
from pathlib import Path
from typing import Any

DEFAULT_LOG_FILE = "isc_certification_report.log"
MAX_LOG_META_CHARS = 4000
STEP_RUNNING_HEARTBEAT_SECONDS = 120

_active_logger: "ReportLogger | None" = None


class StepStatus(str, Enum):
    RUNNING = "RUNNING"
    OK = "OK"
    WARN = "WARN"
    ERROR = "ERROR"
    SKIP = "SKIP"


def resolve_log_path(config: dict[str, Any], base_dir: Path | None = None) -> Path:
    """
    Resolve log file path from config.

    If log_file is empty or omitted, use DEFAULT_LOG_FILE in base_dir (current directory).
    Creates parent directories and the file if they do not exist.
    """
    base = (base_dir or Path.cwd()).resolve()
    raw = config.get("log_file")
    if raw is None or (isinstance(raw, str) and not raw.strip()):
        path = base / DEFAULT_LOG_FILE
    else:
        path = Path(str(raw).strip())
        if not path.is_absolute():
            path = base / path

    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        path.touch()
    return path


def set_active_logger(logger: "ReportLogger | None") -> None:
    global _active_logger
    _active_logger = logger


def get_active_logger() -> "ReportLogger | None":
    return _active_logger


def _truncate_text(text: str, limit: int = MAX_LOG_META_CHARS) -> str:
    if len(text) <= limit:
        return text
    return text[:limit] + f"... (truncated, {len(text)} chars total)"


def _format_body(body: Any) -> str:
    if isinstance(body, str):
        return body
    if isinstance(body, (dict, list)):
        return json.dumps(body, indent=2, default=str)
    return str(body)


class ReportLogger:
    def __init__(self, path: Path, script_name: str = "") -> None:
        self.path = path.resolve()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if not self.path.exists():
            self.path.touch()
        self.log(f"=== Log started: {script_name or 'ISC certification report'} ===")
        self.log(f"Log file: {self.path}")

    def log(self, message: str) -> None:
        ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        line = f"[{ts}] {message}"
        with self.path.open("a", encoding="utf-8") as handle:
            handle.write(line + "\n")

    def _write_multiline(self, prefix: str, text: str) -> None:
        ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        with self.path.open("a", encoding="utf-8") as handle:
            handle.write(f"[{ts}] {prefix}\n")
            for line in text.splitlines():
                handle.write(f"{line}\n")

    def log_http(
        self,
        method: str,
        url: str,
        *,
        status_code: int | None = None,
        params: dict[str, Any] | None = None,
        request_body: Any = None,
        response_body: Any = None,
        result_count: int | None = None,
        error: str | None = None,
    ) -> None:
        parts = [f"HTTP {method.upper()} {url}"]
        if status_code is not None:
            parts.append(f"status={status_code}")
        if params:
            parts.append(f"params={_truncate_text(json.dumps(params, default=str))}")
        if request_body is not None:
            if isinstance(request_body, str):
                body_text = request_body
            else:
                body_text = json.dumps(request_body, default=str)
            parts.append(f"request={_truncate_text(body_text)}")
        if result_count is not None:
            parts.append(f"result_count={result_count}")
        if error:
            parts.append(f"error={error}")
        self.log(" | ".join(parts))

        if response_body is not None:
            body_text = _format_body(response_body)
            self._write_multiline("API response body:", body_text)


def create_report_logger(
    config: dict[str, Any],
    script_name: str,
    base_dir: Path | None = None,
) -> ReportLogger:
    logger = ReportLogger(resolve_log_path(config, base_dir), script_name=script_name)
    set_active_logger(logger)
    return logger


class StepReporter:
    def __init__(
        self,
        total_steps: int,
        logger: ReportLogger | None = None,
        *,
        running_heartbeat_seconds: int = STEP_RUNNING_HEARTBEAT_SECONDS,
    ) -> None:
        self.total_steps = total_steps
        self._current = 0
        self.logger = logger or get_active_logger()
        self._running_heartbeat_seconds = running_heartbeat_seconds
        self._heartbeat_stop: threading.Event | None = None
        self._heartbeat_thread: threading.Thread | None = None
        self._current_step_title = ""

    def _print(self, message: str) -> None:
        print(message, flush=True)

    def _log(self, message: str) -> None:
        if self.logger:
            self.logger.log(message)

    def _emit_running(self, message: str) -> None:
        self._print(message)
        self._log(message)

    def _heartbeat_loop(self, title: str, stop: threading.Event) -> None:
        while not stop.wait(self._running_heartbeat_seconds):
            ts = datetime.now().strftime("%H:%M:%S")
            self._emit_running(
                f"      [{ts}] status: {StepStatus.RUNNING.value} - "
                f"{title} (still running...)"
            )

    def _start_heartbeat(self, title: str) -> None:
        self._stop_heartbeat()
        self._current_step_title = title
        stop = threading.Event()
        self._heartbeat_stop = stop
        self._heartbeat_thread = threading.Thread(
            target=self._heartbeat_loop,
            args=(title, stop),
            daemon=True,
            name="step-reporter-heartbeat",
        )
        self._heartbeat_thread.start()

    def _stop_heartbeat(self) -> None:
        if self._heartbeat_stop is not None:
            self._heartbeat_stop.set()
        if self._heartbeat_thread is not None and self._heartbeat_thread.is_alive():
            self._heartbeat_thread.join(timeout=0.2)
        self._heartbeat_stop = None
        self._heartbeat_thread = None
        self._current_step_title = ""

    def begin(self, title: str) -> None:
        self._stop_heartbeat()
        self._current += 1
        ts = datetime.now().strftime("%H:%M:%S")
        self._print(f"[{self._current}/{self.total_steps}] {title}")
        self._log(f"[{self._current}/{self.total_steps}] {title}")
        line = f"      [{ts}] status: {StepStatus.RUNNING.value}"
        self._print(line)
        self._log(line)
        self._start_heartbeat(title)

    def detail(self, message: str) -> None:
        self._log(f"      {message}")

    def end(self, status: StepStatus, message: str = "") -> None:
        self._stop_heartbeat()
        ts = datetime.now().strftime("%H:%M:%S")
        suffix = f" - {message}" if message else ""
        line = f"      [{ts}] status: {status.value}{suffix}\n"
        self._print(line)
        self._log(line.rstrip("\n"))
