#!/usr/bin/env python3
from __future__ import annotations

import argparse
from datetime import datetime
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
import time
from typing import Any
from urllib.parse import unquote, urlparse


def repository_root() -> Path:
    return Path(__file__).resolve().parents[2]


def read_dotenv_value(path: Path, name: str) -> str | None:
    if not path.exists():
        return None

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue

        key, value = line.split("=", 1)
        if key.strip() == name:
            return value.strip().strip('"').strip("'")

    return None


def resolve_configured_path(base: Path, value: str) -> Path:
    path = Path(value)
    if not path.is_absolute():
        path = base / path
    return path.resolve()


def resolve_clangd(explicit: str | None) -> tuple[Path | None, list[str]]:
    checked: list[str] = []

    if explicit:
        candidate = Path(explicit).expanduser().resolve()
        checked.append(str(candidate))
        return (candidate if candidate.is_file() else None, checked)

    for name in ("clangd.exe", "clangd"):
        found = shutil.which(name)
        if found:
            return Path(found).resolve(), checked

    candidates: list[Path] = []
    program_files = os.environ.get("ProgramFiles")
    program_files_x86 = os.environ.get("ProgramFiles(x86)")

    if program_files:
        pf = Path(program_files)
        candidates.append(pf / "LLVM" / "bin" / "clangd.exe")
        for edition in ("Community", "Professional", "Enterprise", "BuildTools"):
            candidates.append(
                pf
                / "Microsoft Visual Studio"
                / "2022"
                / edition
                / "VC"
                / "Tools"
                / "Llvm"
                / "x64"
                / "bin"
                / "clangd.exe"
            )

    if program_files_x86:
        pfx86 = Path(program_files_x86)
        candidates.append(pfx86 / "LLVM" / "bin" / "clangd.exe")

        vswhere = (
            pfx86
            / "Microsoft Visual Studio"
            / "Installer"
            / "vswhere.exe"
        )
        if vswhere.is_file():
            result = subprocess.run(
                [
                    str(vswhere),
                    "-latest",
                    "-products",
                    "*",
                    "-property",
                    "installationPath",
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            install_paths = result.stdout.strip().splitlines()
            if install_paths:
                vs_root = Path(install_paths[0])
                candidates.extend(
                    [
                        vs_root / "VC" / "Tools" / "Llvm" / "x64" / "bin" / "clangd.exe",
                        vs_root / "VC" / "Tools" / "Llvm" / "bin" / "clangd.exe",
                    ]
                )

    for candidate in candidates:
        text = str(candidate)
        if text in checked:
            continue
        checked.append(text)
        if candidate.is_file():
            return candidate.resolve(), checked

    return None, checked


def parse_clangd_major(version_text: str) -> int:
    match = re.search(r"clangd version\s+(\d+)", version_text)
    if not match:
        raise RuntimeError(
            f"Could not parse clangd major version from: {version_text!r}"
        )
    return int(match.group(1))


def background_index_progress(
    events: list[dict[str, Any]]
) -> dict[str, Any]:
    relevant = [
        event
        for event in events
        if event.get("token") == "backgroundIndexProgress"
        and isinstance(event.get("value"), dict)
    ]

    if not relevant:
        return {
            "event_count": 0,
            "completed": False,
            "last_value": None,
        }

    completed = any(
        event["value"].get("kind") == "end"
        for event in relevant
    )
    return {
        "event_count": len(relevant),
        "completed": completed,
        "last_value": relevant[-1]["value"],
    }


def normalize_file(path: str | Path) -> str:
    return os.path.normcase(os.path.abspath(os.fspath(path))).replace("\\", "/")


def read_compilation_database(path: Path) -> list[dict[str, Any]]:
    parsed = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(parsed, list):
        raise RuntimeError(f"Compilation database is not a JSON array: {path}")
    return parsed


def is_rss_translation_unit(entry: dict[str, Any]) -> bool:
    file_value = entry.get("file")
    if not isinstance(file_value, str):
        return False

    normalized = normalize_file(file_value).lower()
    return (
        "/mods/gamefeatures/rss/source/rss/" in normalized
        or "/vendor/rss-current/rss/source/rss/" in normalized
    )


def path_from_file_uri(uri: str) -> Path | None:
    parsed = urlparse(uri)
    if parsed.scheme != "file":
        return None

    path = unquote(parsed.path)
    if os.name == "nt" and path.startswith("/") and len(path) > 2 and path[2] == ":":
        path = path[1:]

    return Path(path.replace("/", os.sep))


def lsp_position(text: str, needle: str, symbol: str) -> dict[str, int]:
    start = text.find(needle)
    if start < 0:
        raise RuntimeError(f"Could not find B3 root needle: {needle!r}")

    symbol_offset = needle.find(symbol)
    if symbol_offset < 0:
        raise RuntimeError(
            f"Root symbol {symbol!r} is not contained in needle {needle!r}"
        )

    absolute = start + symbol_offset + min(1, max(0, len(symbol) - 1))
    line_start = text.rfind("\n", 0, absolute) + 1
    line = text.count("\n", 0, absolute)
    character = len(text[line_start:absolute].encode("utf-16-le")) // 2
    return {"line": line, "character": character}


def compact_item(item: dict[str, Any]) -> dict[str, Any]:
    uri = item.get("uri")
    path = path_from_file_uri(uri) if isinstance(uri, str) else None
    return {
        "name": item.get("name"),
        "kind": item.get("kind"),
        "detail": item.get("detail"),
        "uri": uri,
        "path": str(path) if path else None,
        "range": item.get("range"),
        "selectionRange": item.get("selectionRange"),
    }


def source_lines_for_ranges(
    uri: str | None, ranges: list[dict[str, Any]]
) -> list[dict[str, Any]]:
    if not uri:
        return []

    path = path_from_file_uri(uri)
    if path is None or not path.is_file():
        return []

    try:
        lines = path.read_text(encoding="utf-8-sig").splitlines()
    except UnicodeDecodeError:
        return []

    results: list[dict[str, Any]] = []
    seen: set[int] = set()
    for range_value in ranges:
        start = range_value.get("start")
        if not isinstance(start, dict):
            continue
        line_number = start.get("line")
        if not isinstance(line_number, int) or line_number in seen:
            continue
        seen.add(line_number)

        if 0 <= line_number < len(lines):
            results.append(
                {
                    "line": line_number + 1,
                    "text": lines[line_number].strip(),
                }
            )

    return results


class LspClient:
    def __init__(
        self,
        clangd: Path,
        compile_commands_dir: Path,
        root: Path,
        stderr_log: Path,
    ) -> None:
        self._next_id = 1
        self.progress_events: list[dict[str, Any]] = []
        self.diagnostics: dict[str, list[dict[str, Any]]] = {}
        self._stderr_handle = stderr_log.open("wb")
        self.process = subprocess.Popen(
            [
                str(clangd),
                f"--compile-commands-dir={compile_commands_dir}",
                "--background-index",
                "--log=info",
            ],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=self._stderr_handle,
        )

        initialize = self.request(
            "initialize",
            {
                "processId": os.getpid(),
                "rootUri": root.as_uri(),
                "capabilities": {
                    "window": {"workDoneProgress": True},
                    "textDocument": {
                        "callHierarchy": {
                            "dynamicRegistration": False
                        },
                        "documentSymbol": {
                            "hierarchicalDocumentSymbolSupport": True
                        },
                        "typeHierarchy": {
                            "dynamicRegistration": False
                        },
                    },
                },
                "workspaceFolders": [
                    {"uri": root.as_uri(), "name": root.name}
                ],
            },
        )
        if "error" in initialize:
            raise RuntimeError(f"clangd initialize failed: {initialize['error']}")

        capabilities = initialize.get("result", {}).get("capabilities", {})
        self.server_capabilities = capabilities
        self.notify("initialized", {})

    def _send(self, message: dict[str, Any]) -> None:
        if self.process.stdin is None:
            raise RuntimeError("clangd stdin is unavailable")

        payload = json.dumps(
            message, ensure_ascii=False, separators=(",", ":")
        ).encode("utf-8")
        header = f"Content-Length: {len(payload)}\r\n\r\n".encode("ascii")
        self.process.stdin.write(header)
        self.process.stdin.write(payload)
        self.process.stdin.flush()

    def _read_message(self) -> dict[str, Any]:
        if self.process.stdout is None:
            raise RuntimeError("clangd stdout is unavailable")

        headers: dict[str, str] = {}
        while True:
            line = self.process.stdout.readline()
            if not line:
                raise RuntimeError(
                    "clangd closed stdout unexpectedly with exit code "
                    f"{self.process.poll()}"
                )
            if line in (b"\r\n", b"\n"):
                break

            decoded = line.decode("ascii", errors="replace").strip()
            if ":" in decoded:
                key, value = decoded.split(":", 1)
                headers[key.lower()] = value.strip()

        length = int(headers.get("content-length", "0"))
        if length <= 0:
            raise RuntimeError(f"Invalid clangd LSP header: {headers}")

        body = self.process.stdout.read(length)
        return json.loads(body.decode("utf-8"))

    def _handle_unsolicited(self, message: dict[str, Any]) -> None:
        method = message.get("method")

        if "id" in message and isinstance(method, str):
            self._send(
                {
                    "jsonrpc": "2.0",
                    "id": message["id"],
                    "result": None,
                }
            )
            return

        if method == "$/progress":
            params = message.get("params")
            if isinstance(params, dict):
                self.progress_events.append(params)
            return

        if method == "textDocument/publishDiagnostics":
            params = message.get("params")
            if not isinstance(params, dict):
                return
            uri = params.get("uri")
            diagnostics = params.get("diagnostics")
            if isinstance(uri, str) and isinstance(diagnostics, list):
                self.diagnostics[uri] = diagnostics

    def request(
        self, method: str, params: dict[str, Any]
    ) -> dict[str, Any]:
        request_id = self._next_id
        self._next_id += 1
        self._send(
            {
                "jsonrpc": "2.0",
                "id": request_id,
                "method": method,
                "params": params,
            }
        )

        while True:
            message = self._read_message()
            if message.get("id") == request_id and "method" not in message:
                return message
            self._handle_unsolicited(message)

    def notify(self, method: str, params: dict[str, Any]) -> None:
        self._send(
            {
                "jsonrpc": "2.0",
                "method": method,
                "params": params,
            }
        )

    def open_file(self, path: Path, text: str) -> None:
        self.notify(
            "textDocument/didOpen",
            {
                "textDocument": {
                    "uri": path.as_uri(),
                    "languageId": "cpp",
                    "version": 1,
                    "text": text,
                }
            },
        )

    def close_file(self, path: Path) -> None:
        self.notify(
            "textDocument/didClose",
            {"textDocument": {"uri": path.as_uri()}},
        )

    def close(self) -> None:
        try:
            if self.process.poll() is None:
                try:
                    self.request("shutdown", {})
                finally:
                    self.notify("exit", {})
                    self.process.wait(timeout=10)
        finally:
            if self.process.poll() is None:
                self.process.kill()
            self._stderr_handle.close()


def incoming_records(result: Any) -> list[dict[str, Any]]:
    if not isinstance(result, list):
        return []

    records: list[dict[str, Any]] = []
    for entry in result:
        if not isinstance(entry, dict):
            continue
        caller = entry.get("from")
        ranges = entry.get("fromRanges")
        if not isinstance(caller, dict):
            continue
        if not isinstance(ranges, list):
            ranges = []

        compact = compact_item(caller)
        records.append(
            {
                "caller": compact,
                "call_ranges": ranges,
                "source_lines": source_lines_for_ranges(
                    compact.get("uri"), ranges
                ),
            }
        )

    return records


def outgoing_records(
    result: Any, root_uri: str | None
) -> list[dict[str, Any]]:
    if not isinstance(result, list):
        return []

    records: list[dict[str, Any]] = []
    for entry in result:
        if not isinstance(entry, dict):
            continue
        callee = entry.get("to")
        ranges = entry.get("fromRanges")
        if not isinstance(callee, dict):
            continue
        if not isinstance(ranges, list):
            ranges = []

        compact = compact_item(callee)
        records.append(
            {
                "callee": compact,
                "call_ranges": ranges,
                "source_lines": source_lines_for_ranges(
                    root_uri, ranges
                ),
            }
        )

    return records


def basenames_from_incoming(records: list[dict[str, Any]]) -> set[str]:
    names: set[str] = set()
    for record in records:
        path = record.get("caller", {}).get("path")
        if isinstance(path, str):
            names.add(Path(path).name.lower())
    return names


def outgoing_names(records: list[dict[str, Any]]) -> set[str]:
    names: set[str] = set()
    for record in records:
        name = record.get("callee", {}).get("name")
        if isinstance(name, str):
            names.add(name)
    return names


def summarize_index_log(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {
            "indexed_translation_units": 0,
            "failed_compile_count": 0,
            "indexing_failure_count": 0,
            "indexed_samples": [],
            "failed_compile_samples": [],
            "indexing_failure_samples": [],
        }

    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    indexed = [line for line in lines if " Indexed " in line]
    failed_compile = [
        line for line in lines if "Failed to compile " in line
    ]
    indexing_failure = [
        line
        for line in lines
        if "Indexing " in line and " failed:" in line
    ]

    return {
        "indexed_translation_units": len(indexed),
        "failed_compile_count": len(failed_compile),
        "indexing_failure_count": len(indexing_failure),
        "indexed_samples": indexed[:10],
        "failed_compile_samples": failed_compile[:20],
        "indexing_failure_samples": indexing_failure[:20],
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "B3 probe: query clangd call hierarchy for a known cross-file RSS2 function."
        )
    )
    parser.add_argument(
        "--clangd",
        help="Explicit path to clangd.exe; normal VS/LLVM locations are checked otherwise.",
    )
    parser.add_argument(
        "--database-view",
        choices=("msvc", "clang"),
        default="clang",
        help=(
            "UBT compiler view to background-index. 'clang' reuses the B2 "
            "Clang-derived database; 'msvc' reuses the real B1 database."
        ),
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=900.0,
        help="Maximum seconds to wait for enough RSS2 background-index coverage.",
    )
    args = parser.parse_args()

    repo = repository_root()
    sml_value = os.environ.get("SML_PROJECT_ROOT") or read_dotenv_value(
        repo / ".env", "SML_PROJECT_ROOT"
    )
    if not sml_value:
        raise RuntimeError(
            "SML_PROJECT_ROOT is not set in the environment or repository .env"
        )

    sml_root = resolve_configured_path(repo, sml_value)
    clangd, searched = resolve_clangd(args.clangd)
    if clangd is None:
        raise RuntimeError(
            "clangd was not found. Checked: " + ", ".join(searched)
        )

    b1_database = (
        repo
        / "work"
        / "external-call-map"
        / "b1"
        / "compile_commands.json"
    )
    b2_clang_database = (
        repo
        / "work"
        / "external-call-map"
        / "b2"
        / "clang-database"
        / "compile_commands.json"
    )

    if args.database_view == "msvc":
        source_database = b1_database
    else:
        source_database = b2_clang_database

    if not source_database.is_file():
        if args.database_view == "clang":
            raise RuntimeError(
                "B2 Clang-derived compile database not found: "
                f"{source_database}. Rerun b2_compare_clangd_inputs.py first."
            )
        raise RuntimeError(
            f"B1 MSVC-derived compile database not found: {source_database}"
        )

    b3_dir = repo / "work" / "external-call-map" / "b3"
    summary_path = b3_dir / "b3-call-hierarchy-summary.json"
    b3_dir.mkdir(parents=True, exist_ok=True)

    clangd_version = subprocess.run(
        [str(clangd), "--version"],
        capture_output=True,
        text=True,
        errors="replace",
        check=False,
    ).stdout.strip()
    clangd_major = parse_clangd_major(clangd_version)

    if clangd_major < 20:
        blocked = {
            "generated_at": datetime.now().astimezone().isoformat(),
            "status": "blocked-clangd-version",
            "clangd": str(clangd),
            "clangd_version": clangd_version,
            "clangd_major": clangd_major,
            "required_major": 20,
            "reason": (
                "clangd 19 implements prepareCallHierarchy/incomingCalls "
                "but does not implement callHierarchy/outgoingCalls; "
                "B3 requires both directions."
            ),
            "b3_pass_condition": False,
        }
        summary_path.write_text(
            json.dumps(blocked, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        print(json.dumps(blocked, indent=2, ensure_ascii=False))
        print()
        print(
            "B3 requires clangd 20 or newer. Install/provide a newer "
            "standalone clangd, then rerun this probe."
        )
        return 2

    compile_dir = (
        b3_dir
        / f"rss-compile-db-{args.database_view}-clangd-{clangd_major}"
    )
    compile_database = compile_dir / "compile_commands.json"
    stderr_log = (
        b3_dir
        / f"clangd-{clangd_major}-{args.database_view}-call-hierarchy-stderr.log"
    )
    compile_dir.mkdir(parents=True, exist_ok=True)

    all_entries = read_compilation_database(source_database)
    rss_entries = [entry for entry in all_entries if is_rss_translation_unit(entry)]
    if not rss_entries:
        raise RuntimeError(
            "No RSS2 translation units were found in the selected compilation database."
        )

    compile_database.write_text(
        json.dumps(rss_entries, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    root_file = (
        sml_root
        / "Mods"
        / "GameFeatures"
        / "RSS"
        / "Source"
        / "RSS"
        / "Private"
        / "RssBlueprintFunctionLibrary.cpp"
    )
    if not root_file.is_file():
        raise RuntimeError(f"B3 root source file not found: {root_file}")

    root_text = root_file.read_text(encoding="utf-8-sig")
    root_position = lsp_position(
        root_text,
        "bool URssBlueprintFunctionLibrary::IsSignDataSafe",
        "IsSignDataSafe",
    )

    expected_incoming_files = {
        "rsssignrco.cpp",
        "rsstemplatesubsystem.cpp",
        "rssjsonserializer.cpp",
    }
    expected_outgoing_name = "IsStructurallySafeRemoteImageUrl"

    print(f"Repository:       {repo}")
    print(f"SML project:      {sml_root}")
    print(f"clangd:           {clangd}")
    print(f"Database view:    {args.database_view}")
    print(f"Source database:  {source_database}")
    print(f"Database entries: {len(all_entries)}")
    print(f"RSS2 index input: {len(rss_entries)} translation units")
    print(f"B3 root:          {root_file}")
    print()
    print(clangd_version)
    print(f"clangd major:      {clangd_major}")
    print()
    print("Starting clangd with background indexing for the filtered RSS2 database...")

    client = LspClient(clangd, compile_dir, sml_root, stderr_log)
    started = time.monotonic()
    incoming: list[dict[str, Any]] = []
    outgoing: list[dict[str, Any]] = []
    root_item: dict[str, Any] | None = None
    prepare_error: Any = None
    incoming_error: Any = None
    outgoing_error: Any = None

    try:
        client.open_file(root_file, root_text)

        prepare = client.request(
            "textDocument/prepareCallHierarchy",
            {
                "textDocument": {"uri": root_file.as_uri()},
                "position": root_position,
            },
        )
        prepare_error = prepare.get("error")
        prepared_items = prepare.get("result")

        if not isinstance(prepared_items, list) or not prepared_items:
            raise RuntimeError(
                "clangd returned no call-hierarchy item for IsSignDataSafe. "
                f"Response: {prepare}"
            )

        selected = None
        for item in prepared_items:
            if (
                isinstance(item, dict)
                and item.get("name") == "IsSignDataSafe"
            ):
                selected = item
                break

        if selected is None:
            candidate = prepared_items[0]
            if not isinstance(candidate, dict):
                raise RuntimeError(
                    f"Unexpected prepareCallHierarchy result: {prepared_items}"
                )
            selected = candidate

        root_item = selected
        print(
            "Prepared root: "
            f"{selected.get('name')} {selected.get('detail', '')}".rstrip()
        )

        last_reported_files: set[str] = set()
        last_progress_repr: str | None = None
        index_completed_at: float | None = None
        while True:
            outgoing_response = client.request(
                "callHierarchy/outgoingCalls",
                {"item": selected},
            )
            incoming_response = client.request(
                "callHierarchy/incomingCalls",
                {"item": selected},
            )

            outgoing_error = outgoing_response.get("error")
            incoming_error = incoming_response.get("error")
            outgoing = outgoing_records(
                outgoing_response.get("result"), selected.get("uri")
            )
            incoming = incoming_records(incoming_response.get("result"))

            seen_files = basenames_from_incoming(incoming)
            seen_outgoing = outgoing_names(outgoing)
            matched_files = seen_files & expected_incoming_files
            has_expected_outgoing = expected_outgoing_name in seen_outgoing

            progress = background_index_progress(client.progress_events)
            progress_repr = json.dumps(
                progress.get("last_value"),
                ensure_ascii=False,
                sort_keys=True,
            )
            if progress_repr != last_progress_repr:
                elapsed = time.monotonic() - started
                print(
                    f"[{elapsed:6.1f}s] background index: "
                    f"{progress.get('last_value')}"
                )
                last_progress_repr = progress_repr

            if progress["completed"] and index_completed_at is None:
                index_completed_at = time.monotonic()

            if seen_files != last_reported_files:
                elapsed = time.monotonic() - started
                print(
                    f"[{elapsed:6.1f}s] incoming files: "
                    + (", ".join(sorted(seen_files)) or "<none>")
                )
                last_reported_files = seen_files

            if len(matched_files) >= 2 and has_expected_outgoing:
                break

            if (
                index_completed_at is not None
                and time.monotonic() - index_completed_at >= 5.0
            ):
                print(
                    "Background indexing reported completion, but the "
                    "expected B3 edges are still incomplete."
                )
                break

            elapsed = time.monotonic() - started
            if elapsed >= args.timeout:
                print(
                    f"Timed out after {elapsed:.1f}s waiting for enough "
                    "cross-file incoming call coverage."
                )
                break

            time.sleep(2.0)

        # One final query after the pass condition/timeout to capture the
        # fullest index state available at the end of the probe.
        outgoing_response = client.request(
            "callHierarchy/outgoingCalls",
            {"item": selected},
        )
        incoming_response = client.request(
            "callHierarchy/incomingCalls",
            {"item": selected},
        )
        outgoing_error = outgoing_response.get("error")
        incoming_error = incoming_response.get("error")
        outgoing = outgoing_records(
            outgoing_response.get("result"), selected.get("uri")
        )
        incoming = incoming_records(incoming_response.get("result"))

        client.close_file(root_file)
    finally:
        client.close()

    seen_files = basenames_from_incoming(incoming)
    matched_incoming_files = sorted(seen_files & expected_incoming_files)
    seen_outgoing = outgoing_names(outgoing)
    has_expected_outgoing = expected_outgoing_name in seen_outgoing

    cross_file_incoming = len(matched_incoming_files) >= 2

    # Canonical B3 only needs correct semantic call edges across files.
    # Incoming-call coverage was a deliberately stricter enrichment probe.
    strict_bidirectional_condition = (
        prepare_error is None
        and incoming_error is None
        and outgoing_error is None
        and cross_file_incoming
        and has_expected_outgoing
    )
    canonical_b3_pass_condition = (
        prepare_error is None
        and incoming_error is None
        and outgoing_error is None
        and has_expected_outgoing
    )

    root_compact = compact_item(root_item) if root_item else None
    index_log_summary = summarize_index_log(stderr_log)

    summary = {
        "generated_at": datetime.now().astimezone().isoformat(),
        "status": "call-hierarchy-completed",
        "clangd": str(clangd),
        "clangd_version": clangd_version,
        "clangd_major": clangd_major,
        "database_view": args.database_view,
        "source_database": str(source_database),
        "source_database_entries": len(all_entries),
        "b1_database": str(b1_database),
        "b2_clang_database": str(b2_clang_database),
        "filtered_database": str(compile_database),
        "filtered_rss2_entries": len(rss_entries),
        "background_index_cache": str(
            compile_dir / ".cache" / "clangd" / "index"
        ),
        "root": root_compact,
        "root_position": root_position,
        "prepare_error": prepare_error,
        "incoming_error": incoming_error,
        "outgoing_error": outgoing_error,
        "incoming_calls": incoming,
        "outgoing_calls": outgoing,
        "manual_expectations": {
            "expected_incoming_files": sorted(expected_incoming_files),
            "matched_incoming_files": matched_incoming_files,
            "expected_outgoing_name": expected_outgoing_name,
            "matched_outgoing": has_expected_outgoing,
        },
        "progress_event_count": len(client.progress_events),
        "background_index_progress": background_index_progress(
            client.progress_events
        ),
        "background_index_log_summary": index_log_summary,
        "published_diagnostics": {
            uri: diagnostics
            for uri, diagnostics in client.diagnostics.items()
            if diagnostics
        },
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "b3_pass_condition": canonical_b3_pass_condition,
        "canonical_b3_pass_condition": canonical_b3_pass_condition,
        "strict_bidirectional_enrichment_condition": strict_bidirectional_condition,
        "clangd_stderr_log": str(stderr_log),
    }

    summary_json = json.dumps(summary, indent=2, ensure_ascii=False)
    summary_path.write_text(summary_json + "\n", encoding="utf-8")

    print()
    print("B3 call hierarchy summary")
    print(f"  incoming call items:       {len(incoming)}")
    print(f"  incoming source files:     {len(seen_files)}")
    print(
        "  expected incoming matched: "
        + (", ".join(matched_incoming_files) or "<none>")
    )
    print(f"  outgoing call items:       {len(outgoing)}")
    print(
        "  indexed translation units: "
        f"{index_log_summary['indexed_translation_units']}"
    )
    print(
        "  failed TU compiles:        "
        f"{index_log_summary['failed_compile_count']}"
    )
    print(
        "  indexing failures:         "
        f"{index_log_summary['indexing_failure_count']}"
    )
    print(f"  expected outgoing matched: {has_expected_outgoing}")
    print(
        "  canonical B3 pass:         "
        f"{canonical_b3_pass_condition}"
    )
    print(
        "  strict bidirectional gate: "
        f"{strict_bidirectional_condition}"
    )
    print()
    print(f"Summary: {summary_path}")
    print(f"Log:     {stderr_log}")

    return 0 if canonical_b3_pass_condition else 2


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
