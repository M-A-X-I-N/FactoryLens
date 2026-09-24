#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
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
            install_path = result.stdout.strip().splitlines()
            if install_path:
                vs_root = Path(install_path[0])
                candidates.extend(
                    [
                        vs_root / "VC" / "Tools" / "Llvm" / "x64" / "bin" / "clangd.exe",
                        vs_root / "VC" / "Tools" / "Llvm" / "bin" / "clangd.exe",
                    ]
                )

    for candidate in candidates:
        normalized = str(candidate)
        if normalized in checked:
            continue
        checked.append(normalized)
        if candidate.is_file():
            return candidate.resolve(), checked

    return None, checked


def normalize_file(path: str | Path) -> str:
    return os.path.normcase(os.path.abspath(os.fspath(path)))


def read_compilation_database(path: Path) -> list[dict[str, Any]]:
    parsed = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(parsed, list):
        raise RuntimeError(f"Compilation database is not a JSON array: {path}")
    return parsed


def find_database_entry(
    entries: list[dict[str, Any]], source: Path
) -> dict[str, Any] | None:
    needle = normalize_file(source)
    for entry in entries:
        file_value = entry.get("file")
        if isinstance(file_value, str) and normalize_file(file_value) == needle:
            return entry
    return None


def path_from_file_uri(uri: str) -> str:
    parsed = urlparse(uri)
    if parsed.scheme != "file":
        return uri

    path = unquote(parsed.path)
    if os.name == "nt" and path.startswith("/") and len(path) > 2 and path[2] == ":":
        path = path[1:]
    return path.replace("/", os.sep)


def lsp_position(text: str, needle: str, symbol: str) -> dict[str, int]:
    start = text.find(needle)
    if start < 0:
        raise RuntimeError(f"Could not find query needle: {needle!r}")

    symbol_offset = needle.find(symbol)
    if symbol_offset < 0:
        raise RuntimeError(
            f"Symbol {symbol!r} is not contained in needle {needle!r}"
        )

    absolute = start + symbol_offset + min(1, max(0, len(symbol) - 1))
    line_start = text.rfind("\n", 0, absolute) + 1
    line = text.count("\n", 0, absolute)
    utf16_character = len(
        text[line_start:absolute].encode("utf-16-le")
    ) // 2

    return {"line": line, "character": utf16_character}


def hover_preview(result: Any) -> str | None:
    if not isinstance(result, dict):
        return None

    contents = result.get("contents")
    if isinstance(contents, str):
        value = contents
    elif isinstance(contents, dict):
        value = str(contents.get("value", ""))
    elif isinstance(contents, list):
        parts: list[str] = []
        for item in contents:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict):
                parts.append(str(item.get("value", "")))
        value = "\n".join(parts)
    else:
        return None

    compact = " ".join(value.split())
    return compact[:500] if compact else None


def definition_locations(result: Any) -> list[dict[str, Any]]:
    if result is None:
        return []

    items = result if isinstance(result, list) else [result]
    locations: list[dict[str, Any]] = []

    for item in items:
        if not isinstance(item, dict):
            continue

        uri = item.get("uri") or item.get("targetUri")
        range_value = item.get("range") or item.get("targetSelectionRange")
        if not isinstance(uri, str):
            continue

        locations.append(
            {
                "uri": uri,
                "path": path_from_file_uri(uri),
                "range": range_value,
            }
        )

    return locations


class LspClient:
    def __init__(
        self,
        clangd: Path,
        compile_commands_dir: Path,
        root: Path,
        stderr_log: Path,
    ) -> None:
        self._next_id = 1
        self.diagnostics: dict[str, list[dict[str, Any]]] = {}
        self._stderr_handle = stderr_log.open("wb")
        self.process = subprocess.Popen(
            [
                str(clangd),
                f"--compile-commands-dir={compile_commands_dir}",
                "--background-index=0",
                "--log=error",
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
                    "textDocument": {
                        "definition": {"linkSupport": True},
                        "hover": {"contentFormat": ["markdown", "plaintext"]},
                    }
                },
                "workspaceFolders": [
                    {"uri": root.as_uri(), "name": root.name}
                ],
            },
        )
        if "error" in initialize:
            raise RuntimeError(f"clangd initialize failed: {initialize['error']}")

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
                    f"clangd closed stdout unexpectedly with exit code "
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

    def _record_notification(self, message: dict[str, Any]) -> None:
        if message.get("method") != "textDocument/publishDiagnostics":
            return

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
            if message.get("id") == request_id:
                return message
            self._record_notification(message)

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


def run_view(
    *,
    label: str,
    clangd: Path,
    database_dir: Path,
    database_path: Path,
    sml_root: Path,
    rss_file: Path,
    wiremod_file: Path,
    output_dir: Path,
) -> dict[str, Any]:
    entries = read_compilation_database(database_path)

    missing = [
        str(source)
        for source in (rss_file, wiremod_file)
        if find_database_entry(entries, source) is None
    ]
    if missing:
        return {
            "status": "representative-files-missing",
            "database": str(database_path),
            "missing": missing,
        }

    queries = {
        rss_file: [
            {
                "label": "rss-local-json-serializer",
                "needle": "FRssJsonSerializer::JsonToSignData",
                "symbol": "FRssJsonSerializer",
            },
            {
                "label": "rss-unreal-http-module",
                "needle": "FHttpModule::Get",
                "symbol": "FHttpModule",
            },
        ],
        wiremod_file: [
            {
                "label": "wiremod-local-connection-type",
                "needle": "UConnectionTypeFunctions::IsValidConnectionPair",
                "symbol": "UConnectionTypeFunctions",
            },
            {
                "label": "wiremod-unreal-fstring",
                "needle": "FString CategoryThis",
                "symbol": "FString",
            },
        ],
    }

    log_path = output_dir / f"{label}-semantic-lsp-stderr.log"
    client = LspClient(clangd, database_dir, sml_root, log_path)
    results: list[dict[str, Any]] = []

    try:
        for source, source_queries in queries.items():
            text = source.read_text(encoding="utf-8-sig")
            client.open_file(source, text)

            for query in source_queries:
                position = lsp_position(
                    text, query["needle"], query["symbol"]
                )
                text_document = {"uri": source.as_uri()}

                hover_response = client.request(
                    "textDocument/hover",
                    {
                        "textDocument": text_document,
                        "position": position,
                    },
                )
                definition_response = client.request(
                    "textDocument/definition",
                    {
                        "textDocument": text_document,
                        "position": position,
                    },
                )

                hover_result = hover_response.get("result")
                definitions = definition_locations(
                    definition_response.get("result")
                )

                results.append(
                    {
                        "label": query["label"],
                        "source": str(source),
                        "symbol": query["symbol"],
                        "position": position,
                        "hover_present": hover_result is not None,
                        "hover_preview": hover_preview(hover_result),
                        "definition_count": len(definitions),
                        "definitions": definitions,
                        "hover_error": hover_response.get("error"),
                        "definition_error": definition_response.get("error"),
                    }
                )

            client.close_file(source)

        diagnostics = {
            path_from_file_uri(uri): values
            for uri, values in client.diagnostics.items()
            if values
        }
    finally:
        client.close()

    success = all(
        item["hover_present"]
        and item["definition_count"] > 0
        and item["hover_error"] is None
        and item["definition_error"] is None
        for item in results
    )

    return {
        "status": "completed",
        "database": str(database_path),
        "database_entries": len(entries),
        "semantic_resolution_success": success,
        "results": results,
        "published_diagnostics": diagnostics,
        "clangd_stderr_log": str(log_path),
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "B2 positive semantic-resolution probe for RSS2 and Wiremod."
        )
    )
    parser.add_argument(
        "--clangd",
        help="Explicit path to clangd.exe; normal VS/LLVM locations are checked otherwise.",
    )
    args = parser.parse_args()

    repo = repository_root()
    dotenv = repo / ".env"
    sml_value = os.environ.get("SML_PROJECT_ROOT") or read_dotenv_value(
        dotenv, "SML_PROJECT_ROOT"
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

    b1_dir = repo / "work" / "external-call-map" / "b1"
    b2_dir = repo / "work" / "external-call-map" / "b2"
    clang_db_dir = b2_dir / "clang-database"
    output_path = b2_dir / "b2-semantic-resolution-summary.json"
    b2_dir.mkdir(parents=True, exist_ok=True)

    databases = {
        "msvc": (b1_dir, b1_dir / "compile_commands.json"),
        "clang": (
            clang_db_dir,
            clang_db_dir / "compile_commands.json",
        ),
    }

    rss_file = (
        sml_root
        / "Mods"
        / "GameFeatures"
        / "RSS"
        / "Source"
        / "RSS"
        / "Private"
        / "Api"
        / "RssApiClient.cpp"
    )
    wiremod_file = (
        sml_root
        / "Mods"
        / "FicsitWiremod"
        / "Source"
        / "FicsitWiremod"
        / "Private"
        / "Behaviour"
        / "Displays"
        / "ManagedSign"
        / "ManagedSign.cpp"
    )

    for source in (rss_file, wiremod_file):
        if not source.is_file():
            raise RuntimeError(f"Representative source file not found: {source}")

    clangd_version = subprocess.run(
        [str(clangd), "--version"],
        capture_output=True,
        text=True,
        check=False,
    ).stdout.strip()

    summary: dict[str, Any] = {
        "generated_at": __import__("datetime").datetime.now().astimezone().isoformat(),
        "status": "semantic-resolution-completed",
        "clangd": str(clangd),
        "clangd_version": clangd_version,
        "rss2_file": str(rss_file),
        "wiremod_file": str(wiremod_file),
        "views": {},
    }

    print(f"Repository:  {repo}")
    print(f"SML project: {sml_root}")
    print(f"clangd:      {clangd}")
    print(clangd_version)

    for label, (database_dir, database_path) in databases.items():
        print()
        print(f"==> semantic resolution through {label} database")

        if not database_path.is_file():
            result = {
                "status": "database-missing",
                "database": str(database_path),
            }
        else:
            result = run_view(
                label=label,
                clangd=clangd,
                database_dir=database_dir,
                database_path=database_path,
                sml_root=sml_root,
                rss_file=rss_file,
                wiremod_file=wiremod_file,
                output_dir=b2_dir,
            )

        summary["views"][label] = result
        print(
            json.dumps(
                result,
                indent=2,
                ensure_ascii=False,
            )
        )

    completed = [
        view
        for view in summary["views"].values()
        if view.get("status") == "completed"
    ]
    summary["semantic_resolution_success"] = bool(completed) and all(
        view.get("semantic_resolution_success") is True
        for view in completed
    )

    output_path.write_text(
        json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    print()
    print(f"B2 semantic summary written to: {output_path}")

    return 0 if summary["semantic_resolution_success"] else 2


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
