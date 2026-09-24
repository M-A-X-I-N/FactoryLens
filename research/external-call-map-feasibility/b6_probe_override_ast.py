#!/usr/bin/env python3
from __future__ import annotations

import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from typing import Any

import b3_probe_call_hierarchy as b3
import b4_build_manual_call_map as b4


TARGETS = [
    {
        "symbol": "ARSSSignHologram::GetRotationStep",
        "source": "Mods/GameFeatures/RSS/Source/RSS/Public/Hologram/RSSSignHologram.h",
        "class": "ARSSSignHologram",
        "method": "GetRotationStep",
        "expect_external_override": True,
        "role": "previous-b6-false-negative",
    },
    {
        "symbol": "ARSSSignHologramPipeAndBelts::IsValidHitResult",
        "source": "Mods/GameFeatures/RSS/Source/RSS/Public/Hologram/RSSSignHologramPipeAndBelts.h",
        "class": "ARSSSignHologramPipeAndBelts",
        "method": "IsValidHitResult",
        "expect_external_override": True,
        "role": "previous-b6-false-negative",
    },
    {
        "symbol": "ARssDataManagerSubsystem::Tick",
        "source": "Mods/GameFeatures/RSS/Source/RSS/Public/Subsystem/RSSDataManagerSubsystem.h",
        "class": "ARssDataManagerSubsystem",
        "method": "Tick",
        "expect_external_override": True,
        "role": "known-positive",
    },
    {
        "symbol": "ARssDataManagerSubsystem::CheckCopy",
        "source": "Mods/GameFeatures/RSS/Source/RSS/Public/Subsystem/RSSDataManagerSubsystem.h",
        "class": "ARssDataManagerSubsystem",
        "method": "CheckCopy",
        "expect_external_override": False,
        "role": "ordinary-local-negative",
    },
]


def symbol_position(symbol: dict[str, Any]) -> dict[str, int] | None:
    for key in ("selectionRange", "range"):
        value = symbol.get(key)
        if not isinstance(value, dict):
            continue
        start = value.get("start")
        if (
            isinstance(start, dict)
            and isinstance(start.get("line"), int)
            and isinstance(start.get("character"), int)
        ):
            return {
                "line": start["line"],
                "character": start["character"],
            }
    return None


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

    pos = (position["line"], position["character"])
    begin = (start.get("line"), start.get("character"))
    finish = (end.get("line"), end.get("character"))
    if not all(isinstance(value, int) for value in (*begin, *finish)):
        return False
    return begin <= pos <= finish


def walk_ast(node: Any) -> list[dict[str, Any]]:
    if not isinstance(node, dict):
        return []
    result = [node]
    children = node.get("children")
    if isinstance(children, list):
        for child in children:
            result.extend(walk_ast(child))
    return result


def find_document_method(
    result: Any,
    class_name: str,
    method_name: str,
) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
    if not isinstance(result, list):
        return None, None

    stack = [
        symbol for symbol in result if isinstance(symbol, dict)
    ]
    while stack:
        symbol = stack.pop(0)
        if symbol.get("name") == class_name:
            children = symbol.get("children")
            if isinstance(children, list):
                for child in children:
                    if (
                        isinstance(child, dict)
                        and child.get("name") == method_name
                        and child.get("kind") == 6
                    ):
                        return symbol, child

        children = symbol.get("children")
        if isinstance(children, list):
            stack.extend(
                child for child in children if isinstance(child, dict)
            )

    return None, None


def find_ast_method(
    ast: Any,
    method_name: str,
    position: dict[str, int],
) -> dict[str, Any] | None:
    candidates = []
    for node in walk_ast(ast):
        if node.get("kind") != "CXXMethod":
            continue
        if node.get("detail") != method_name:
            continue
        if not position_in_range(position, node.get("range")):
            continue
        candidates.append(node)

    if not candidates:
        return None

    def span_key(node: dict[str, Any]) -> tuple[int, int]:
        range_value = node.get("range") or {}
        start = range_value.get("start") or {}
        end = range_value.get("end") or {}
        line_span = int(end.get("line", 10**9)) - int(
            start.get("line", 0)
        )
        char_span = int(end.get("character", 10**9)) - int(
            start.get("character", 0)
        )
        return (line_span, char_span)

    return min(candidates, key=span_key)


def override_attrs(
    method_node: dict[str, Any] | None,
) -> list[dict[str, Any]]:
    if method_node is None:
        return []

    attrs = []
    for node in walk_ast(method_node):
        if (
            node.get("role") == "attribute"
            and node.get("kind") in {"Override", "Final"}
        ):
            attrs.append(node)
    return attrs


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


def definition_locations(
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

    raw = response.get("result")
    if isinstance(raw, dict):
        raw = [raw]

    locations = []
    if isinstance(raw, list):
        for item in raw:
            if not isinstance(item, dict):
                continue
            if isinstance(item.get("targetUri"), str):
                uri_value = item["targetUri"]
                range_value = (
                    item.get("targetSelectionRange")
                    if isinstance(
                        item.get("targetSelectionRange"), dict
                    )
                    else item.get("targetRange")
                )
            else:
                uri_value = item.get("uri")
                range_value = item.get("range")

            if (
                isinstance(uri_value, str)
                and isinstance(range_value, dict)
            ):
                locations.append(
                    {
                        "uri": uri_value,
                        "range": range_value,
                    }
                )

    return locations, None


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Targeted B6 diagnostic: use clangd's semantic AST OverrideAttr "
            "and Go-to-Definition behavior to recover direct overridden base "
            "methods without relying on the incomplete reverse index."
        )
    )
    parser.add_argument("--clangd")
    parser.add_argument(
        "--reuse-index",
        action="store_true",
        help=(
            "Reuse B6's existing clangd index cache. Recommended; this probe "
            "does not need a fresh background index."
        ),
    )
    args = parser.parse_args()

    repo = b3.repository_root()
    sml_value = os.environ.get("SML_PROJECT_ROOT") or b3.read_dotenv_value(
        repo / ".env", "SML_PROJECT_ROOT"
    )
    if not sml_value:
        raise RuntimeError("SML_PROJECT_ROOT is not configured")
    sml_root = b3.resolve_configured_path(repo, sml_value)

    clangd, searched = b3.resolve_clangd(args.clangd)
    if clangd is None:
        raise RuntimeError(
            "clangd was not found. Checked: " + ", ".join(searched)
        )

    version = subprocess.run(
        [str(clangd), "--version"],
        capture_output=True,
        text=True,
        errors="replace",
        check=False,
    ).stdout.strip()
    major = b3.parse_clangd_major(version)
    if major != 20:
        raise RuntimeError(
            f"This diagnostic is pinned to clangd 20; got {major}"
        )

    b6_compile_dir = (
        repo
        / "work"
        / "external-call-map"
        / "b6"
        / "rss-compile-db-clangd-20"
    )
    compile_database = b6_compile_dir / "compile_commands.json"
    if not compile_database.is_file():
        raise RuntimeError(
            "B6 filtered compile database not found. Run the main B6 probe "
            "first."
        )

    output_dir = repo / "work" / "external-call-map" / "b6"
    summary_path = output_dir / "b6-override-ast-diagnostic.json"
    stderr_log = output_dir / "clangd-20-b6-override-ast-stderr.log"

    print(f"Repository:  {repo}")
    print(f"SML root:    {sml_root}")
    print(f"clangd:      {clangd}")
    print(f"Reuse index: {args.reuse_index}")
    print()
    print(version)

    started = time.monotonic()
    client = b3.LspClient(
        clangd,
        b6_compile_dir,
        sml_root,
        stderr_log,
    )
    docs = b4.DocumentManager(client)
    results = []

    try:
        for target in TARGETS:
            source = sml_root / target["source"]
            if not source.is_file():
                raise RuntimeError(
                    f"Target source file not found: {source}"
                )
            uri = source.as_uri()
            docs.open_uri(uri)

            symbols_response = client.request(
                "textDocument/documentSymbol",
                {"textDocument": {"uri": uri}},
            )
            if symbols_response.get("error") is not None:
                results.append(
                    {
                        **target,
                        "pass": False,
                        "error": symbols_response.get("error"),
                    }
                )
                continue

            class_symbol, method_symbol = find_document_method(
                symbols_response.get("result"),
                target["class"],
                target["method"],
            )
            method_position = (
                symbol_position(method_symbol)
                if method_symbol is not None
                else None
            )

            if (
                class_symbol is None
                or method_symbol is None
                or method_position is None
            ):
                results.append(
                    {
                        **target,
                        "pass": False,
                        "error": "document symbol target not found",
                    }
                )
                continue

            ast_range = class_symbol.get("range")
            ast_response = client.request(
                "textDocument/ast",
                {
                    "textDocument": {"uri": uri},
                    "range": ast_range,
                },
            )
            if ast_response.get("error") is not None:
                results.append(
                    {
                        **target,
                        "pass": False,
                        "error": ast_response.get("error"),
                    }
                )
                continue

            method_node = find_ast_method(
                ast_response.get("result"),
                target["method"],
                method_position,
            )
            attrs = override_attrs(method_node)

            bases = []
            definition_errors = []
            for attr in attrs:
                attr_range = attr.get("range")
                attr_position = (
                    attr_range.get("start")
                    if isinstance(attr_range, dict)
                    else None
                )
                if not (
                    isinstance(attr_position, dict)
                    and isinstance(attr_position.get("line"), int)
                    and isinstance(
                        attr_position.get("character"), int
                    )
                ):
                    continue

                locations, error = definition_locations(
                    client,
                    uri,
                    {
                        "line": attr_position["line"],
                        "character": attr_position["character"],
                    },
                )
                if error is not None:
                    definition_errors.append(error)
                bases.extend(locations)

            compact_bases = [
                compact_location(location) for location in bases
            ]
            external_bases = [
                location
                for location in compact_bases
                if not location["inside_rss2"]
            ]
            actual = bool(external_bases)
            expected = bool(target["expect_external_override"])

            result = {
                **target,
                "document_method_range": method_symbol.get("range"),
                "ast_method": {
                    "kind": method_node.get("kind"),
                    "detail": method_node.get("detail"),
                    "range": method_node.get("range"),
                    "arcana": method_node.get("arcana"),
                }
                if method_node is not None
                else None,
                "override_attributes": [
                    {
                        "kind": attr.get("kind"),
                        "detail": attr.get("detail"),
                        "range": attr.get("range"),
                        "arcana": attr.get("arcana"),
                    }
                    for attr in attrs
                ],
                "base_locations": compact_bases,
                "external_base_locations": external_bases,
                "definition_errors": definition_errors,
                "actual_external_override": actual,
                "pass": actual == expected,
            }
            results.append(result)

            print(
                f"{'PASS' if result['pass'] else 'FAIL'} "
                f"{target['symbol']}: "
                f"{len(attrs)} override/final attr(s), "
                f"{len(external_bases)} external base(s)"
            )

        passed = sum(1 for result in results if result["pass"])
        evidence_ready = (
            len(results) == len(TARGETS)
            and passed == len(TARGETS)
        )

        summary = {
            "generated_at": datetime.now().astimezone().isoformat(),
            "status": "b6-override-ast-diagnostic-completed",
            "clangd": str(clangd),
            "clangd_version": version,
            "compile_database": str(compile_database),
            "targets": results,
            "passed": passed,
            "total": len(results),
            "b6_override_ast_evidence_ready": evidence_ready,
            "elapsed_seconds": round(
                time.monotonic() - started, 3
            ),
            "outputs": {
                "json": str(summary_path),
                "clangd_log": str(stderr_log),
            },
        }
        summary_path.write_text(
            json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )

        print()
        print(
            f"Override-AST evidence ready: {evidence_ready} "
            f"({passed}/{len(results)})"
        )
        print(f"JSON: {summary_path}")
        print(f"Log:  {stderr_log}")
        return 0 if evidence_ready else 2
    finally:
        docs.close_all()
        client.close()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
