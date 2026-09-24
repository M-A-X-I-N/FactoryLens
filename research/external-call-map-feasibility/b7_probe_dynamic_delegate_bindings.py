#!/usr/bin/env python3
from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from typing import Any

import b3_probe_call_hierarchy as b3
import b4_build_manual_call_map as b4


CONTROLS_FILE = Path(__file__).with_name("b7_delegate_controls.json")

# Deliberately narrow Unreal dynamic-delegate registration rule.
# This is not a C++ parser. It only identifies the current project shape:
#   Delegate.AddDynamic(this, &QualifiedClass::Handler)
# Semantic clangd queries then verify the handler target.
BINDING_PATTERN = re.compile(
    r"\.AddDynamic\s*\(\s*[^,\n]+?\s*,\s*&\s*"
    r"(?P<class>[A-Za-z_]\w*(?:::[A-Za-z_]\w*)*)::"
    r"(?P<method>[A-Za-z_]\w*)\s*\)",
    re.MULTILINE,
)


def load_controls() -> dict[str, Any]:
    parsed = json.loads(CONTROLS_FILE.read_text(encoding="utf-8"))
    if not isinstance(parsed, dict):
        raise RuntimeError(f"Invalid B7 controls file: {CONTROLS_FILE}")
    return parsed


def offset_position(text: str, offset: int) -> dict[str, int]:
    if offset < 0 or offset > len(text):
        raise ValueError(f"Offset outside document: {offset}")

    line = text.count("\n", 0, offset)
    last_newline = text.rfind("\n", 0, offset)
    character = offset if last_newline < 0 else offset - last_newline - 1
    return {"line": line, "character": character}


def position_in_range(
    position: dict[str, int],
    range_value: Any,
) -> bool:
    if not isinstance(range_value, dict):
        return False
    start = range_value.get("start")
    end = range_value.get("end")
    if not isinstance(start, dict) or not isinstance(end, dict):
        return False

    values = (
        start.get("line"),
        start.get("character"),
        end.get("line"),
        end.get("character"),
    )
    if not all(isinstance(value, int) for value in values):
        return False

    pos = (position["line"], position["character"])
    begin = (start["line"], start["character"])
    finish = (end["line"], end["character"])
    return begin <= pos <= finish


def flatten_symbols(
    symbols: Any,
    *,
    container: list[str] | None = None,
) -> list[dict[str, Any]]:
    if not isinstance(symbols, list):
        return []

    current_container = list(container or [])
    result: list[dict[str, Any]] = []
    for symbol in symbols:
        if not isinstance(symbol, dict):
            continue

        entry = dict(symbol)
        entry["_container"] = list(current_container)
        result.append(entry)

        children = symbol.get("children")
        if isinstance(children, list):
            name = symbol.get("name")
            next_container = list(current_container)
            if isinstance(name, str) and name:
                next_container.append(name)
            result.extend(
                flatten_symbols(
                    children,
                    container=next_container,
                )
            )
    return result


def enclosing_callable(
    symbols: Any,
    position: dict[str, int],
) -> dict[str, Any] | None:
    candidates = []
    for symbol in flatten_symbols(symbols):
        if symbol.get("kind") not in {6, 9, 12}:
            continue
        if not position_in_range(position, symbol.get("range")):
            continue
        candidates.append(symbol)

    if not candidates:
        return None

    def span_key(symbol: dict[str, Any]) -> tuple[int, int]:
        range_value = symbol.get("range") or {}
        start = range_value.get("start") or {}
        end = range_value.get("end") or {}
        return (
            int(end.get("line", 10**9))
            - int(start.get("line", 0)),
            int(end.get("character", 10**9))
            - int(start.get("character", 0)),
        )

    return min(candidates, key=span_key)


def qualified_symbol_name(symbol: dict[str, Any] | None) -> str | None:
    if symbol is None:
        return None
    name = symbol.get("name")
    if not isinstance(name, str) or not name:
        return None

    container = [
        item
        for item in symbol.get("_container", [])
        if isinstance(item, str) and item
    ]
    return "::".join(container + [name]) if container else name


def lsp_locations(result: Any) -> list[dict[str, Any]]:
    if isinstance(result, dict):
        raw = [result]
    elif isinstance(result, list):
        raw = result
    else:
        return []

    locations = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        if isinstance(item.get("targetUri"), str):
            uri = item["targetUri"]
            range_value = (
                item.get("targetSelectionRange")
                if isinstance(item.get("targetSelectionRange"), dict)
                else item.get("targetRange")
            )
        else:
            uri = item.get("uri")
            range_value = item.get("range")

        if isinstance(uri, str) and isinstance(range_value, dict):
            locations.append({"uri": uri, "range": range_value})

    return locations


def compact_location(location: dict[str, Any]) -> dict[str, Any]:
    uri = location.get("uri")
    path = (
        b3.path_from_file_uri(uri)
        if isinstance(uri, str)
        else None
    )
    return {
        "uri": uri,
        "path": str(path) if path is not None else None,
        "project_path": b4.project_path(path),
        "range": location.get("range"),
        "inside_rss2": b4.is_rss_project_path(path),
    }


def request_definition(
    client: b3.LspClient,
    uri: str,
    position: dict[str, int],
) -> tuple[list[dict[str, Any]], Any]:
    response = client.request(
        "textDocument/definition",
        {
            "textDocument": {"uri": uri},
            "position": position,
        },
    )
    if response.get("error") is not None:
        return [], response.get("error")
    return lsp_locations(response.get("result")), None


def walk_ast(node: Any) -> list[dict[str, Any]]:
    if not isinstance(node, dict):
        return []
    result = [node]
    children = node.get("children")
    if isinstance(children, list):
        for child in children:
            result.extend(walk_ast(child))
    return result


def compact_ast_evidence(
    ast: Any,
    *,
    handler_method: str,
) -> list[dict[str, Any]]:
    needles = (
        "AddDynamic",
        "__Internal_AddDynamic",
        handler_method,
    )
    evidence = []
    for node in walk_ast(ast):
        detail = str(node.get("detail") or "")
        arcana = str(node.get("arcana") or "")
        if not any(
            needle in detail or needle in arcana
            for needle in needles
        ):
            continue

        evidence.append(
            {
                "kind": node.get("kind"),
                "role": node.get("role"),
                "detail": node.get("detail"),
                "range": node.get("range"),
                "arcana": node.get("arcana"),
            }
        )
        if len(evidence) >= 20:
            break
    return evidence


def binding_matches(text: str) -> list[re.Match[str]]:
    return list(BINDING_PATTERN.finditer(text))


def source_line(text: str, line: int) -> str:
    lines = text.splitlines()
    if 0 <= line < len(lines):
        return lines[line]
    return ""


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "B7 probe: identify RSS2 dynamic-delegate callback roots from "
            "narrow AddDynamic registration rules and verify the handler "
            "targets semantically with clangd."
        )
    )
    parser.add_argument(
        "--clangd",
        help=(
            "Explicit clangd 20 executable. The WinGet LLVM.clangd 20.1.8 "
            "portable alias is recommended."
        ),
    )
    args = parser.parse_args()

    repo = b3.repository_root()
    sml_value = os.environ.get("SML_PROJECT_ROOT") or b3.read_dotenv_value(
        repo / ".env", "SML_PROJECT_ROOT"
    )
    if not sml_value:
        raise RuntimeError(
            "SML_PROJECT_ROOT is not set in the environment or repository .env"
        )
    sml_root = b3.resolve_configured_path(repo, sml_value)

    rss_root = (
        sml_root
        / "Mods"
        / "GameFeatures"
        / "RSS"
        / "Source"
        / "RSS"
    )
    if not rss_root.is_dir():
        raise RuntimeError(f"RSS2 source root not found: {rss_root}")

    clangd, searched = b3.resolve_clangd(args.clangd)
    if clangd is None:
        raise RuntimeError(
            "clangd was not found. Checked: " + ", ".join(searched)
        )

    clangd_version = subprocess.run(
        [str(clangd), "--version"],
        capture_output=True,
        text=True,
        errors="replace",
        check=False,
    ).stdout.strip()
    clangd_major = b3.parse_clangd_major(clangd_version)
    if clangd_major != 20:
        raise RuntimeError(
            "B7 is pinned to the B3-B6-proven clangd 20 semantic stack. "
            f"Resolved clangd major {clangd_major}: {clangd}"
        )

    # B7 depends on B6 and can reuse the exact same RSS2-only compile database
    # and warm index cache. The delegate proof itself uses foreground AST and
    # definition queries rather than requiring a fresh project index.
    compile_dir = (
        repo
        / "work"
        / "external-call-map"
        / "b6"
        / "rss-compile-db-clangd-20"
    )
    compile_database = compile_dir / "compile_commands.json"
    if not compile_database.is_file():
        raise RuntimeError(
            "B6 RSS2 compile workspace not found. Complete B6 first: "
            f"{compile_database}"
        )

    controls = load_controls()
    expected_handlers = {
        str(value)
        for value in controls.get("expected_unique_handlers", [])
    }
    expected_registration_count = int(
        controls.get("expected_registration_count", 0)
    )

    cpp_files = sorted(path for path in rss_root.rglob("*.cpp") if path.is_file())

    output_dir = repo / "work" / "external-call-map" / "b7"
    output_dir.mkdir(parents=True, exist_ok=True)
    summary_path = output_dir / "b7-dynamic-delegate-bindings.json"
    roots_path = output_dir / "b7-dynamic-delegate-roots.txt"
    stderr_log = output_dir / "clangd-20-b7-delegates-stderr.log"

    print(f"Repository:       {repo}")
    print(f"SML project:      {sml_root}")
    print(f"RSS2 source root: {rss_root}")
    print(f"clangd:           {clangd}")
    print(f"C++ source files: {len(cpp_files)}")
    print(f"B6 workspace:     {compile_dir}")
    print()
    print(clangd_version)

    started = time.monotonic()
    client = b3.LspClient(clangd, compile_dir, sml_root, stderr_log)
    docs = b4.DocumentManager(client)

    registrations: list[dict[str, Any]] = []
    unresolved: list[dict[str, Any]] = []
    macro_definition_locations: list[dict[str, Any]] = []

    try:
        for source in cpp_files:
            text = source.read_text(
                encoding="utf-8-sig",
                errors="replace",
            )
            matches = binding_matches(text)
            if not matches:
                continue

            uri = source.as_uri()
            docs.open_uri(uri)

            symbol_response = client.request(
                "textDocument/documentSymbol",
                {"textDocument": {"uri": uri}},
            )
            document_symbols = (
                symbol_response.get("result")
                if symbol_response.get("error") is None
                else []
            )

            print(
                f"{source.relative_to(rss_root)}: "
                f"{len(matches)} AddDynamic registration(s)"
            )

            for match in matches:
                class_name = match.group("class")
                method_name = match.group("method")
                handler_symbol = f"{class_name}::{method_name}"

                handler_offset = match.start("method")
                handler_position = offset_position(text, handler_offset)

                add_offset = text.find(
                    "AddDynamic",
                    match.start(),
                    match.end(),
                )
                if add_offset < 0:
                    unresolved.append(
                        {
                            "source": str(source),
                            "handler_symbol": handler_symbol,
                            "reason": "AddDynamic token not found inside match",
                        }
                    )
                    continue
                add_position = offset_position(text, add_offset)

                handler_defs, handler_error = request_definition(
                    client,
                    uri,
                    handler_position,
                )
                compact_handler_defs = [
                    compact_location(location)
                    for location in handler_defs
                ]
                project_handler_defs = [
                    location
                    for location in compact_handler_defs
                    if location["inside_rss2"]
                ]

                macro_defs, macro_error = request_definition(
                    client,
                    uri,
                    add_position,
                )
                compact_macro_defs = [
                    compact_location(location)
                    for location in macro_defs
                ]
                macro_definition_locations.extend(compact_macro_defs)

                enclosing = enclosing_callable(
                    document_symbols,
                    add_position,
                )

                line_number = add_position["line"]
                line_text = source_line(text, line_number)
                line_range = {
                    "start": {"line": line_number, "character": 0},
                    "end": {
                        "line": line_number,
                        "character": len(line_text),
                    },
                }
                ast_response = client.request(
                    "textDocument/ast",
                    {
                        "textDocument": {"uri": uri},
                        "range": line_range,
                    },
                )
                ast_error = ast_response.get("error")
                ast_evidence = (
                    compact_ast_evidence(
                        ast_response.get("result"),
                        handler_method=method_name,
                    )
                    if ast_error is None
                    else []
                )

                resolved = bool(project_handler_defs)
                registration = {
                    "family": "unreal-dynamic-delegate-handler",
                    "handler_symbol": handler_symbol,
                    "handler_class": class_name,
                    "handler_method": method_name,
                    "registration_source": {
                        "path": str(source),
                        "project_path": b4.project_path(source),
                        "line": line_number + 1,
                        "range": {
                            "start": offset_position(
                                text, match.start()
                            ),
                            "end": offset_position(
                                text, match.end()
                            ),
                        },
                        "source_line": line_text.strip(),
                    },
                    "enclosing_registration_callable": {
                        "name": qualified_symbol_name(enclosing),
                        "range": (
                            enclosing.get("range")
                            if enclosing is not None
                            else None
                        ),
                    },
                    "handler_definition_locations": (
                        compact_handler_defs
                    ),
                    "project_handler_definition_locations": (
                        project_handler_defs
                    ),
                    "handler_definition_error": handler_error,
                    "add_dynamic_definition_locations": (
                        compact_macro_defs
                    ),
                    "add_dynamic_definition_error": macro_error,
                    "foreground_ast_evidence": ast_evidence,
                    "foreground_ast_error": ast_error,
                    "semantic_handler_resolved": resolved,
                    "candidate_root": resolved,
                    "candidate_reason": (
                        "RSS2 method is registered as the callback argument of "
                        "an Unreal AddDynamic delegate binding and the handler "
                        "reference resolves semantically back into RSS2"
                    ),
                }
                registrations.append(registration)

                if not resolved:
                    unresolved.append(
                        {
                            "source": str(source),
                            "line": line_number + 1,
                            "handler_symbol": handler_symbol,
                            "handler_definition_error": handler_error,
                            "handler_definitions": compact_handler_defs,
                        }
                    )

        unique_handlers: dict[str, dict[str, Any]] = {}
        for registration in registrations:
            if not registration["semantic_handler_resolved"]:
                continue
            symbol = str(registration["handler_symbol"])
            entry = unique_handlers.get(symbol)
            if entry is None:
                entry = {
                    "family": "unreal-dynamic-delegate-handler",
                    "symbol": symbol,
                    "candidate_root": True,
                    "registration_count": 0,
                    "registration_sites": [],
                    "handler_definition_locations": (
                        registration[
                            "project_handler_definition_locations"
                        ]
                    ),
                }
                unique_handlers[symbol] = entry
            entry["registration_count"] += 1
            entry["registration_sites"].append(
                registration["registration_source"]
            )

        discovered_handlers = set(unique_handlers)
        missing_expected_handlers = sorted(
            expected_handlers - discovered_handlers
        )
        unexpected_handlers = sorted(
            discovered_handlers - expected_handlers
        )

        unique_macro_defs: dict[str, dict[str, Any]] = {}
        for location in macro_definition_locations:
            key = json.dumps(location, sort_keys=True)
            unique_macro_defs[key] = location

        controls_pass = (
            len(registrations) >= expected_registration_count
            and not unresolved
            and not missing_expected_handlers
        )

        elapsed = time.monotonic() - started
        summary = {
            "generated_at": datetime.now().astimezone().isoformat(),
            "status": "dynamic-delegate-enrichment-completed",
            "clangd": str(clangd),
            "clangd_version": clangd_version,
            "clangd_major": clangd_major,
            "compile_database": str(compile_database),
            "rss2_source_root": str(rss_root),
            "discovery_rule": (
                "narrow .AddDynamic(receiver, &QualifiedClass::Handler) "
                "registration rule; handler target accepted only after clangd "
                "definition resolution returns an RSS2 declaration"
            ),
            "metrics": {
                "cpp_files_scanned": len(cpp_files),
                "registration_count": len(registrations),
                "resolved_registration_count": sum(
                    1
                    for registration in registrations
                    if registration["semantic_handler_resolved"]
                ),
                "unique_handler_count": len(unique_handlers),
                "source_file_count_with_bindings": len(
                    {
                        registration["registration_source"]["project_path"]
                        for registration in registrations
                    }
                ),
                "unresolved_registration_count": len(unresolved),
                "elapsed_seconds": round(elapsed, 3),
            },
            "controls": {
                "expected_registration_count": (
                    expected_registration_count
                ),
                "expected_unique_handlers": sorted(expected_handlers),
                "missing_expected_handlers": missing_expected_handlers,
                "unexpected_handlers": unexpected_handlers,
                "pass": controls_pass,
            },
            "add_dynamic_definition_locations": list(
                unique_macro_defs.values()
            ),
            "registrations": registrations,
            "unique_handler_roots": sorted(
                unique_handlers.values(),
                key=lambda item: item["symbol"],
            ),
            "unresolved": unresolved,
            "b7_delegate_family_evidence_ready": controls_pass,
            "outputs": {
                "json": str(summary_path),
                "roots": str(roots_path),
                "clangd_log": str(stderr_log),
            },
        }

        summary_path.write_text(
            json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )

        lines = [
            "B7 Unreal dynamic-delegate callback roots",
            f"clangd: {clangd_version.splitlines()[0]}",
            (
                f"registrations: {len(registrations)} | "
                f"unique handlers: {len(unique_handlers)} | "
                f"unresolved: {len(unresolved)}"
            ),
            "",
        ]
        for item in summary["unique_handler_roots"]:
            lines.append(
                f"{item['symbol']} "
                f"({item['registration_count']} registration(s))"
            )
            for site in item["registration_sites"]:
                lines.append(
                    f"  <- {site['project_path']}:{site['line']}"
                )
        roots_path.write_text(
            "\n".join(lines).rstrip() + "\n",
            encoding="utf-8",
        )

        print()
        print(
            f"Registrations: {len(registrations)} | "
            f"Unique handlers: {len(unique_handlers)} | "
            f"Unresolved: {len(unresolved)}"
        )
        print(
            "B7 delegate-family evidence ready: "
            f"{controls_pass}"
        )
        print(f"JSON:  {summary_path}")
        print(f"Roots: {roots_path}")
        print(f"Log:   {stderr_log}")

        return 0 if controls_pass else 2
    finally:
        docs.close_all()
        client.close()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
