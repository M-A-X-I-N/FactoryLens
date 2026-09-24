#!/usr/bin/env python3
from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import datetime
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from typing import Any
from urllib.parse import unquote, urlparse

import b3_probe_call_hierarchy as b3
import b4_build_manual_call_map as b4


CONTROLS_FILE = Path(__file__).with_name("b6_known_controls.json")

CLASS_LIKE_KINDS = {5, 11, 23}
METHOD_KIND = 6


def load_controls() -> list[dict[str, Any]]:
    parsed = json.loads(CONTROLS_FILE.read_text(encoding="utf-8"))
    controls = parsed.get("controls")
    if not isinstance(controls, list):
        raise RuntimeError(f"Invalid B6 controls file: {CONTROLS_FILE}")
    return [item for item in controls if isinstance(item, dict)]


def rss_source_root(sml_root: Path) -> Path:
    root = (
        sml_root
        / "Mods"
        / "GameFeatures"
        / "RSS"
        / "Source"
        / "RSS"
    )
    if not root.is_dir():
        raise RuntimeError(f"RSS2 source root not found: {root}")
    return root


def location_path(uri: str | None) -> Path | None:
    if not isinstance(uri, str):
        return None
    return b3.path_from_file_uri(uri)


def range_start(range_value: Any) -> dict[str, int] | None:
    if not isinstance(range_value, dict):
        return None
    start = range_value.get("start")
    if not isinstance(start, dict):
        return None
    line = start.get("line")
    character = start.get("character")
    if not isinstance(line, int) or not isinstance(character, int):
        return None
    return {"line": line, "character": character}


def symbol_position(symbol: dict[str, Any]) -> dict[str, int] | None:
    return range_start(symbol.get("selectionRange")) or range_start(
        symbol.get("range")
    )


def location_key(location: dict[str, Any]) -> tuple[str, int] | None:
    uri = location.get("uri")
    path = location_path(uri)
    start = range_start(location.get("range"))
    if path is None or start is None:
        return None
    return (b3.normalize_file(path).lower(), start["line"])


def symbol_location(uri: str, symbol: dict[str, Any]) -> dict[str, Any]:
    range_value = symbol.get("selectionRange")
    if not isinstance(range_value, dict):
        range_value = symbol.get("range")
    return {
        "uri": uri,
        "range": range_value,
    }


def compact_location(location: dict[str, Any] | None) -> dict[str, Any] | None:
    if not isinstance(location, dict):
        return None
    uri = location.get("uri")
    path = location_path(uri)
    return {
        "uri": uri,
        "path": str(path) if path is not None else None,
        "project_path": b4.project_path(path),
        "range": location.get("range"),
    }


def compact_type_item(
    item: dict[str, Any],
    *,
    depth: int,
) -> dict[str, Any]:
    uri = item.get("uri")
    path = location_path(uri)
    return {
        "name": item.get("name"),
        "kind": item.get("kind"),
        "uri": uri,
        "path": str(path) if path is not None else None,
        "project_path": b4.project_path(path),
        "range": item.get("range"),
        "selectionRange": item.get("selectionRange"),
        "depth": depth,
        "inside_rss2": b4.is_rss_project_path(path),
    }


def iter_document_symbols(
    symbols: Any,
    *,
    parent: dict[str, Any] | None = None,
) -> list[tuple[dict[str, Any], dict[str, Any] | None]]:
    if not isinstance(symbols, list):
        return []

    result: list[tuple[dict[str, Any], dict[str, Any] | None]] = []
    for symbol in symbols:
        if not isinstance(symbol, dict):
            continue
        result.append((symbol, parent))
        children = symbol.get("children")
        if isinstance(children, list):
            result.extend(
                iter_document_symbols(children, parent=symbol)
            )
    return result


def class_methods_from_document_symbols(
    symbols: Any,
) -> list[tuple[dict[str, Any], list[dict[str, Any]]]]:
    classes: list[tuple[dict[str, Any], list[dict[str, Any]]]] = []
    if not isinstance(symbols, list):
        return classes

    def visit(symbol: dict[str, Any]) -> None:
        kind = symbol.get("kind")
        children = symbol.get("children")
        if kind in CLASS_LIKE_KINDS and isinstance(children, list):
            methods = [
                child
                for child in children
                if isinstance(child, dict)
                and child.get("kind") == METHOD_KIND
            ]
            if methods:
                classes.append((symbol, methods))

        if isinstance(children, list):
            for child in children:
                if isinstance(child, dict):
                    visit(child)

    for symbol in symbols:
        if isinstance(symbol, dict):
            visit(symbol)

    return classes


def prepare_type_item(
    client: b3.LspClient,
    uri: str,
    class_symbol: dict[str, Any],
) -> tuple[dict[str, Any] | None, Any]:
    position = symbol_position(class_symbol)
    if position is None:
        return None, "class symbol has no usable selection range"

    response = client.request(
        "textDocument/prepareTypeHierarchy",
        {
            "textDocument": {"uri": uri},
            "position": position,
        },
    )
    if response.get("error") is not None:
        return None, response.get("error")

    result = response.get("result")
    if not isinstance(result, list):
        return None, "prepareTypeHierarchy returned no list"

    expected_name = class_symbol.get("name")
    items = [item for item in result if isinstance(item, dict)]
    for item in items:
        if item.get("name") == expected_name:
            return item, None
    return (items[0], None) if items else (None, "no type item")


def type_item_key(item: dict[str, Any]) -> tuple[str, str, int, int]:
    uri = str(item.get("uri") or "")
    name = str(item.get("name") or "")
    position = range_start(item.get("selectionRange")) or {"line": -1, "character": -1}
    return (uri, name, position["line"], position["character"])


def collect_supertypes(
    client: b3.LspClient,
    root_item: dict[str, Any],
    *,
    max_depth: int,
    cache: dict[
        tuple[str, str, int, int],
        tuple[list[dict[str, Any]], Any],
    ],
) -> tuple[list[tuple[dict[str, Any], int]], list[dict[str, Any]]]:
    collected: list[tuple[dict[str, Any], int]] = []
    errors: list[dict[str, Any]] = []
    queue: list[tuple[dict[str, Any], int]] = [(root_item, 0)]
    seen = {type_item_key(root_item)}

    while queue:
        item, depth = queue.pop(0)
        if depth >= max_depth:
            continue

        key = type_item_key(item)
        cached = cache.get(key)
        if cached is None:
            response = client.request(
                "typeHierarchy/supertypes",
                {"item": item},
            )
            error = response.get("error")
            raw = response.get("result")
            parents = [
                parent
                for parent in raw
                if isinstance(parent, dict)
            ] if isinstance(raw, list) else []
            cached = (parents, error)
            cache[key] = cached

        parents, error = cached
        if error is not None:
            errors.append(
                {
                    "type": compact_type_item(item, depth=depth),
                    "error": error,
                }
            )
            continue

        for parent in parents:
            parent_key = type_item_key(parent)
            if parent_key in seen:
                continue
            seen.add(parent_key)
            next_depth = depth + 1
            collected.append((parent, next_depth))
            queue.append((parent, next_depth))

    return collected, errors


def symbol_information_location(
    symbol: dict[str, Any],
) -> dict[str, Any] | None:
    location = symbol.get("location")
    if isinstance(location, dict):
        return location

    # LSP 3.17 workspace symbols may use a location with only a URI until
    # resolve. clangd 20 normally returns a full Location, but keep the shape
    # explicit rather than guessing a range.
    return None


def container_matches(container: Any, ancestor_name: str) -> bool:
    if not isinstance(container, str):
        return False
    normalized = container.strip()
    return (
        normalized == ancestor_name
        or normalized.endswith(f"::{ancestor_name}")
    )


def workspace_base_methods(
    client: b3.LspClient,
    *,
    ancestor_name: str,
    method_name: str,
    cache: dict[tuple[str, str], tuple[list[dict[str, Any]], Any]],
) -> tuple[list[dict[str, Any]], Any]:
    cache_key = (ancestor_name, method_name)
    cached = cache.get(cache_key)
    if cached is not None:
        return cached

    response = client.request(
        "workspace/symbol",
        {"query": f"{ancestor_name}::{method_name}"},
    )
    error = response.get("error")
    raw = response.get("result")
    candidates = []
    if isinstance(raw, list):
        for symbol in raw:
            if not isinstance(symbol, dict):
                continue
            if symbol.get("name") != method_name:
                continue
            if not container_matches(
                symbol.get("containerName"), ancestor_name
            ):
                continue
            location = symbol_information_location(symbol)
            if location is None:
                continue
            path = location_path(location.get("uri"))
            if path is None or b4.is_rss_project_path(path):
                continue
            candidates.append(symbol)

    cached = (candidates, error)
    cache[cache_key] = cached
    return cached


def definition_locations(
    client: b3.LspClient,
    uri: str,
    method: dict[str, Any],
) -> tuple[list[dict[str, Any]], Any]:
    positions = symbol_position(method)
    own = symbol_location(uri, method)
    locations = [own]

    if positions is None:
        return locations, "method symbol has no usable selection range"

    response = client.request(
        "textDocument/definition",
        {
            "textDocument": {"uri": uri},
            "position": positions,
        },
    )
    error = response.get("error")
    raw = response.get("result")
    for location in b4.definition_locations(raw):
        if location not in locations:
            # b4 returns uri + position; turn it back into a Location-like
            # zero-width range for matching by source line.
            pos = location["position"]
            locations.append(
                {
                    "uri": location["uri"],
                    "range": {
                        "start": pos,
                        "end": pos,
                    },
                }
            )

    return locations, error


def implementation_locations(
    client: b3.LspClient,
    docs: b4.DocumentManager,
    base_location: dict[str, Any],
) -> tuple[list[dict[str, Any]], Any]:
    uri = base_location.get("uri")
    position = range_start(base_location.get("range"))
    if not isinstance(uri, str) or position is None:
        return [], "base workspace symbol has no usable location"

    base_path = docs.open_uri(uri)
    if base_path is None:
        return [], "external base document could not be opened for AST"

    response = client.request(
        "textDocument/implementation",
        {
            "textDocument": {"uri": uri},
            "position": position,
        },
    )
    error = response.get("error")
    raw = response.get("result")

    if isinstance(raw, dict):
        raw = [raw]
    locations = [
        item for item in raw if isinstance(item, dict)
    ] if isinstance(raw, list) else []
    return locations, error


def any_location_matches(
    implementations: list[dict[str, Any]],
    project_locations: list[dict[str, Any]],
) -> tuple[bool, dict[str, Any] | None]:
    expected = {
        key
        for location in project_locations
        if (key := location_key(location)) is not None
    }

    for implementation in implementations:
        key = location_key(implementation)
        if key is not None and key in expected:
            return True, implementation

    return False, None


def control_map(
    controls: list[dict[str, Any]],
) -> dict[str, dict[str, Any]]:
    return {
        str(control["symbol"]): control
        for control in controls
        if isinstance(control.get("symbol"), str)
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "B6 semantic probe: discover RSS2 methods whose override relation "
            "is proven by clangd against a virtual method declared outside the "
            "RSS2 project boundary."
        )
    )
    parser.add_argument(
        "--clangd",
        help=(
            "Explicit clangd 20 executable. The WinGet LLVM.clangd 20.1.8 "
            "portable alias is recommended."
        ),
    )
    parser.add_argument(
        "--max-super-depth",
        type=int,
        default=12,
        help="Maximum semantic supertype depth to inspect per RSS2 class.",
    )
    parser.add_argument(
        "--index-timeout",
        type=float,
        default=240.0,
        help="Maximum seconds to wait for RSS2 background indexing.",
    )
    parser.add_argument(
        "--reuse-index",
        action="store_true",
        help=(
            "Reuse B6's ignored clangd index cache. Default is a cold B6 "
            "index so the first configured result is reproducible."
        ),
    )
    args = parser.parse_args()

    if args.max_super_depth < 1:
        raise RuntimeError("--max-super-depth must be at least 1")

    repo = b3.repository_root()
    sml_value = os.environ.get("SML_PROJECT_ROOT") or b3.read_dotenv_value(
        repo / ".env", "SML_PROJECT_ROOT"
    )
    if not sml_value:
        raise RuntimeError(
            "SML_PROJECT_ROOT is not set in the environment or repository .env"
        )
    sml_root = b3.resolve_configured_path(repo, sml_value)
    rss_root = rss_source_root(sml_root)

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
            "B6 is pinned to the B3-B5-proven clangd 20 semantic stack. "
            f"Resolved clangd major {clangd_major}: {clangd}"
        )

    source_database = (
        repo
        / "work"
        / "external-call-map"
        / "b2"
        / "clang-database"
        / "compile_commands.json"
    )
    if not source_database.is_file():
        raise RuntimeError(
            f"B2 Clang-derived compile database not found: {source_database}"
        )

    entries = b3.read_compilation_database(source_database)
    rss_entries = [
        entry for entry in entries if b3.is_rss_translation_unit(entry)
    ]
    if not rss_entries:
        raise RuntimeError("No RSS2 translation units found in B2 database")

    output_dir = repo / "work" / "external-call-map" / "b6"
    compile_dir = output_dir / "rss-compile-db-clangd-20"
    compile_database = compile_dir / "compile_commands.json"
    summary_path = output_dir / "b6-external-overrides.json"
    candidates_path = output_dir / "b6-external-overrides.txt"
    stderr_log = output_dir / "clangd-20-b6-stderr.log"
    compile_dir.mkdir(parents=True, exist_ok=True)
    output_dir.mkdir(parents=True, exist_ok=True)

    compile_database.write_text(
        json.dumps(rss_entries, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    cache_dir = compile_dir / ".cache"
    cache_existed_before = cache_dir.exists()
    cache_cleared = False
    if not args.reuse_index and cache_dir.exists():
        import shutil
        shutil.rmtree(cache_dir)
        cache_cleared = True

    headers = sorted(
        path
        for path in rss_root.rglob("*.h")
        if path.is_file()
    )
    controls = load_controls()
    controls_by_symbol = control_map(controls)

    first_source = Path(str(rss_entries[0]["file"]))
    if not first_source.is_file():
        raise RuntimeError(
            f"First RSS2 translation unit not found: {first_source}"
        )
    first_text = first_source.read_text(
        encoding="utf-8-sig", errors="replace"
    )
    first_position = {"line": 0, "character": 0}

    print(f"Repository:       {repo}")
    print(f"SML project:      {sml_root}")
    print(f"RSS2 source root: {rss_root}")
    print(f"clangd:           {clangd}")
    print(f"RSS2 TUs:         {len(rss_entries)}")
    print(f"RSS2 headers:     {len(headers)}")
    print(f"Cold index:       {not args.reuse_index}")
    print()
    print(clangd_version)

    total_started = time.monotonic()
    client = b3.LspClient(
        clangd,
        compile_dir,
        sml_root,
        stderr_log,
    )
    docs = b4.DocumentManager(client)

    super_cache: dict[
        tuple[str, str, int, int],
        tuple[list[dict[str, Any]], Any],
    ] = {}
    workspace_cache: dict[
        tuple[str, str],
        tuple[list[dict[str, Any]], Any],
    ] = {}

    header_errors: list[dict[str, Any]] = []
    type_errors: list[dict[str, Any]] = []
    query_errors: list[dict[str, Any]] = []
    candidates: list[dict[str, Any]] = []
    classes_examined = 0
    methods_examined = 0
    external_ancestor_count = 0

    try:
        docs.open_uri(first_source.as_uri())
        index_progress = b4.wait_for_background_index(
            client,
            first_source.as_uri(),
            first_position,
            args.index_timeout,
        )

        for header_index, header in enumerate(headers, start=1):
            uri = header.as_uri()
            docs.open_uri(uri)

            response = client.request(
                "textDocument/documentSymbol",
                {"textDocument": {"uri": uri}},
            )
            if response.get("error") is not None:
                header_errors.append(
                    {
                        "header": str(header),
                        "error": response.get("error"),
                    }
                )
                continue

            class_methods = class_methods_from_document_symbols(
                response.get("result")
            )
            if not class_methods:
                continue

            print(
                f"[{header_index:02d}/{len(headers):02d}] "
                f"{header.relative_to(rss_root)}: "
                f"{len(class_methods)} class-like symbol(s)"
            )

            for class_symbol, methods in class_methods:
                classes_examined += 1
                class_name = str(class_symbol.get("name") or "")
                type_item, type_error = prepare_type_item(
                    client, uri, class_symbol
                )
                if type_item is None:
                    type_errors.append(
                        {
                            "class": class_name,
                            "header": str(header),
                            "error": type_error,
                        }
                    )
                    continue

                supertypes, super_errors = collect_supertypes(
                    client,
                    type_item,
                    max_depth=args.max_super_depth,
                    cache=super_cache,
                )
                type_errors.extend(super_errors)

                external_ancestors = [
                    (item, depth)
                    for item, depth in supertypes
                    if not b4.is_rss_project_path(
                        location_path(item.get("uri"))
                    )
                ]
                if not external_ancestors:
                    continue

                external_ancestor_count += len(external_ancestors)

                for method in methods:
                    methods_examined += 1
                    method_name = str(method.get("name") or "")
                    if not method_name:
                        continue

                    project_locations, definition_error = (
                        definition_locations(client, uri, method)
                    )
                    if definition_error is not None:
                        query_errors.append(
                            {
                                "stage": "project-definition",
                                "symbol": f"{class_name}::{method_name}",
                                "error": definition_error,
                            }
                        )

                    matched_records: list[dict[str, Any]] = []
                    for ancestor, depth in external_ancestors:
                        ancestor_name = str(ancestor.get("name") or "")
                        if not ancestor_name:
                            continue

                        base_symbols, workspace_error = workspace_base_methods(
                            client,
                            ancestor_name=ancestor_name,
                            method_name=method_name,
                            cache=workspace_cache,
                        )
                        if workspace_error is not None:
                            query_errors.append(
                                {
                                    "stage": "workspace-symbol",
                                    "symbol": (
                                        f"{ancestor_name}::{method_name}"
                                    ),
                                    "error": workspace_error,
                                }
                            )
                            continue

                        for base_symbol in base_symbols:
                            base_location = symbol_information_location(
                                base_symbol
                            )
                            if base_location is None:
                                continue

                            implementations, implementation_error = (
                                implementation_locations(
                                    client, docs, base_location
                                )
                            )
                            if implementation_error is not None:
                                query_errors.append(
                                    {
                                        "stage": "implementation",
                                        "symbol": (
                                            f"{ancestor_name}::{method_name}"
                                        ),
                                        "error": implementation_error,
                                    }
                                )
                                continue

                            matched, matched_location = any_location_matches(
                                implementations,
                                project_locations,
                            )
                            if not matched:
                                continue

                            matched_records.append(
                                {
                                    "base_container": base_symbol.get(
                                        "containerName"
                                    ),
                                    "base_symbol": base_symbol.get("name"),
                                    "base_declaration": compact_location(
                                        base_location
                                    ),
                                    "ancestor": compact_type_item(
                                        ancestor, depth=depth
                                    ),
                                    "project_implementation_returned": (
                                        compact_location(matched_location)
                                    ),
                                    "semantic_proof": (
                                        "clangd textDocument/implementation "
                                        "on the external base method returned "
                                        "this RSS2 method location"
                                    ),
                                }
                            )

                    if not matched_records:
                        continue

                    # Multiple ancestor declarations can prove the same override
                    # through an override chain. Keep them as evidence but emit
                    # one candidate per RSS2 method.
                    symbol_name = f"{class_name}::{method_name}"
                    control = controls_by_symbol.get(symbol_name)

                    candidate = {
                        "symbol": symbol_name,
                        "class": class_name,
                        "method": method_name,
                        "project_declaration": compact_location(
                            symbol_location(uri, method)
                        ),
                        "project_definition_locations": [
                            compact_location(location)
                            for location in project_locations
                        ],
                        "external_override": True,
                        "candidate_root": True,
                        "candidate_reason": (
                            "virtual override relation proven against a method "
                            "declared outside the RSS2 project boundary"
                        ),
                        "semantic_evidence": matched_records,
                        "control_role": (
                            control.get("role")
                            if control is not None
                            else None
                        ),
                        "control_reason": (
                            control.get("reason")
                            if control is not None
                            else None
                        ),
                    }
                    candidates.append(candidate)
                    print(f"    + {symbol_name}")

        candidates.sort(key=lambda item: item["symbol"])
        discovered = {candidate["symbol"] for candidate in candidates}

        control_results = []
        for control in controls:
            symbol = str(control.get("symbol") or "")
            expected = bool(control.get("expect_external_override"))
            actual = symbol in discovered
            control_results.append(
                {
                    **control,
                    "actual_external_override_candidate": actual,
                    "pass": actual == expected,
                }
            )

        positive_controls = [
            result
            for result in control_results
            if result.get("expect_external_override") is True
        ]
        negative_controls = [
            result
            for result in control_results
            if result.get("expect_external_override") is False
        ]
        lifecycle_controls = [
            result
            for result in control_results
            if result.get("role") == "lifecycle-positive"
        ]
        utility_controls = [
            result
            for result in control_results
            if result.get("role") == "generic-override-control"
        ]

        lifecycle_pass = all(
            result["pass"] for result in lifecycle_controls
        )
        utility_pass = all(
            result["pass"] for result in utility_controls
        )
        ordinary_local_pass = all(
            result["pass"] for result in negative_controls
        )

        index_log_summary = b3.summarize_index_log(stderr_log)

        evidence_ready = (
            len(candidates) >= 8
            and lifecycle_pass
            and ordinary_local_pass
            and len(query_errors) == 0
        )

        elapsed = time.monotonic() - total_started
        summary = {
            "generated_at": datetime.now().astimezone().isoformat(),
            "status": "external-override-discovery-completed",
            "clangd": str(clangd),
            "clangd_version": clangd_version,
            "clangd_major": clangd_major,
            "source_database": str(source_database),
            "filtered_database": str(compile_database),
            "filtered_rss2_entries": len(rss_entries),
            "rss2_source_root": str(rss_root),
            "headers_discovered": len(headers),
            "controls_file": str(CONTROLS_FILE),
            "limits": {
                "max_super_depth": args.max_super_depth,
                "index_timeout_seconds": args.index_timeout,
            },
            "index_cache": {
                "reuse_requested": args.reuse_index,
                "cache_existed_before": cache_existed_before,
                "cache_cleared_for_cold_run": cache_cleared,
            },
            "background_index_progress": index_progress,
            "background_index_log_summary": index_log_summary,
            "metrics": {
                "classes_examined": classes_examined,
                "methods_examined": methods_examined,
                "external_ancestor_relations_examined": (
                    external_ancestor_count
                ),
                "external_override_candidate_count": len(candidates),
                "header_error_count": len(header_errors),
                "type_hierarchy_error_count": len(type_errors),
                "semantic_query_error_count": len(query_errors),
                "elapsed_seconds": round(elapsed, 3),
            },
            "control_results": control_results,
            "control_summary": {
                "lifecycle_positive_pass": lifecycle_pass,
                "generic_override_control_pass": utility_pass,
                "ordinary_local_negative_pass": ordinary_local_pass,
                "reverse_index_candidate_set_ready": evidence_ready,
                "generic_override_controls_are_coverage_diagnostics": True,
                "passed": sum(
                    1 for result in control_results if result["pass"]
                ),
                "total": len(control_results),
            },
            "candidates": candidates,
            "errors": {
                "headers": header_errors,
                "type_hierarchy": type_errors,
                "semantic_queries": query_errors,
            },
            "b6_evidence_ready": evidence_ready,
            "outputs": {
                "json": str(summary_path),
                "candidates": str(candidates_path),
                "clangd_log": str(stderr_log),
            },
        }

        summary_path.write_text(
            json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )

        lines = [
            "B6 automatic external-override candidates",
            f"clangd: {clangd_version.splitlines()[0]}",
            f"candidates: {len(candidates)}",
            (
                "controls: "
                f"{summary['control_summary']['passed']}/"
                f"{summary['control_summary']['total']} passed"
            ),
            "",
        ]
        for candidate in candidates:
            evidence = candidate["semantic_evidence"][0]
            base = evidence.get("base_container") or "?"
            role = candidate.get("control_role")
            role_suffix = f" [{role}]" if role else ""
            lines.append(
                f"{candidate['symbol']} -> {base}::{candidate['method']}"
                f"{role_suffix}"
            )
            declaration = candidate.get("project_declaration") or {}
            base_decl = evidence.get("base_declaration") or {}
            lines.append(
                "  project: "
                + str(declaration.get("project_path") or declaration.get("path"))
            )
            lines.append(
                "  base:    "
                + str(base_decl.get("project_path") or base_decl.get("path"))
            )
        candidates_path.write_text(
            "\n".join(lines).rstrip() + "\n",
            encoding="utf-8",
        )

        print()
        print(
            f"Candidates: {len(candidates)} | "
            f"Controls: {summary['control_summary']['passed']}/"
            f"{summary['control_summary']['total']} | "
            f"B6 evidence ready: {evidence_ready}"
        )
        print(f"JSON:       {summary_path}")
        print(f"Candidates: {candidates_path}")
        print(f"Log:        {stderr_log}")

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
