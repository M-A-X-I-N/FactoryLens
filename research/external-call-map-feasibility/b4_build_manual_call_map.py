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

import b3_probe_call_hierarchy as b3


ROOTS_FILE = Path(__file__).with_name("b4_manual_roots.json")


def load_root(root_id: str) -> dict[str, Any]:
    parsed = json.loads(ROOTS_FILE.read_text(encoding="utf-8"))
    roots = parsed.get("roots")
    if not isinstance(roots, list):
        raise RuntimeError(f"Invalid B4 roots file: {ROOTS_FILE}")

    for root in roots:
        if isinstance(root, dict) and root.get("id") == root_id:
            return root

    available = [
        root.get("id")
        for root in roots
        if isinstance(root, dict) and isinstance(root.get("id"), str)
    ]
    raise RuntimeError(
        f"Unknown B4 root {root_id!r}. Available: {', '.join(available)}"
    )


def is_rss_project_path(path: Path | None) -> bool:
    if path is None:
        return False

    normalized = b3.normalize_file(path).lower()
    return (
        "/mods/gamefeatures/rss/source/rss/" in normalized
        or "/vendor/rss-current/rss/source/rss/" in normalized
    )


def project_path(path: Path | None) -> str | None:
    if path is None:
        return None

    normalized = b3.normalize_file(path)
    lowered = normalized.lower()

    markers = (
        "/vendor/rss-current/",
        "/mods/gamefeatures/rss/",
    )
    for marker in markers:
        index = lowered.find(marker)
        if index >= 0:
            suffix = normalized[index + len(marker) :]
            if marker.endswith("/rss/"):
                return f"RSS/{suffix}"
            return suffix

    return str(path)


def item_key(item: dict[str, Any]) -> str:
    uri = item.get("uri")
    selection = item.get("selectionRange", {})
    start = selection.get("start", {}) if isinstance(selection, dict) else {}
    return "|".join(
        [
            str(uri),
            str(start.get("line")),
            str(start.get("character")),
            str(item.get("name")),
        ]
    )


def item_sort_key(item: dict[str, Any]) -> tuple[str, int, int, str]:
    uri = item.get("uri")
    path = b3.path_from_file_uri(uri) if isinstance(uri, str) else None
    relative = project_path(path) or str(path or uri or "")
    selection = item.get("selectionRange", {})
    start = selection.get("start", {}) if isinstance(selection, dict) else {}
    line = start.get("line") if isinstance(start.get("line"), int) else 10**9
    char = (
        start.get("character")
        if isinstance(start.get("character"), int)
        else 10**9
    )
    return (relative.lower(), line, char, str(item.get("name") or ""))


def compact_node_item(item: dict[str, Any]) -> dict[str, Any]:
    compact = b3.compact_item(item)
    path_value = compact.get("path")
    path = Path(path_value) if isinstance(path_value, str) else None

    selection = compact.get("selectionRange")
    start = selection.get("start") if isinstance(selection, dict) else None
    line = start.get("line") + 1 if isinstance(start, dict) and isinstance(start.get("line"), int) else None

    compact["project_path"] = project_path(path)
    compact["line"] = line
    return compact


class DocumentManager:
    def __init__(self, client: b3.LspClient) -> None:
        self.client = client
        self.opened: dict[str, Path] = {}

    def open_uri(self, uri: str) -> Path | None:
        path = b3.path_from_file_uri(uri)
        if path is None or not path.is_file():
            return None

        key = b3.normalize_file(path)
        if key not in self.opened:
            text = path.read_text(encoding="utf-8-sig", errors="replace")
            self.client.open_file(path, text)
            self.opened[key] = path
        return path

    def close_all(self) -> None:
        for path in list(self.opened.values()):
            self.client.close_file(path)
        self.opened.clear()


def choose_prepared_item(
    result: Any, expected_name: str | None
) -> dict[str, Any] | None:
    if not isinstance(result, list) or not result:
        return None

    candidates = [item for item in result if isinstance(item, dict)]
    if not candidates:
        return None

    if expected_name:
        for item in candidates:
            name = item.get("name")
            if name == expected_name:
                return item
            if isinstance(name, str) and name.endswith(f"::{expected_name}"):
                return item

    return candidates[0]


def prepare_at(
    client: b3.LspClient,
    docs: DocumentManager,
    uri: str,
    position: dict[str, int],
    expected_name: str | None,
) -> tuple[dict[str, Any] | None, Any]:
    docs.open_uri(uri)
    response = client.request(
        "textDocument/prepareCallHierarchy",
        {
            "textDocument": {"uri": uri},
            "position": position,
        },
    )
    if response.get("error") is not None:
        return None, response.get("error")

    return choose_prepared_item(response.get("result"), expected_name), None


def definition_locations(result: Any) -> list[dict[str, Any]]:
    raw: list[Any]
    if isinstance(result, list):
        raw = result
    elif isinstance(result, dict):
        raw = [result]
    else:
        return []

    locations: list[dict[str, Any]] = []
    for entry in raw:
        if not isinstance(entry, dict):
            continue

        if isinstance(entry.get("targetUri"), str):
            uri = entry["targetUri"]
            selection = entry.get("targetSelectionRange")
            fallback = entry.get("targetRange")
            range_value = selection if isinstance(selection, dict) else fallback
        else:
            uri = entry.get("uri")
            range_value = entry.get("range")

        if not isinstance(uri, str) or not isinstance(range_value, dict):
            continue

        start = range_value.get("start")
        if not isinstance(start, dict):
            continue
        if not isinstance(start.get("line"), int) or not isinstance(start.get("character"), int):
            continue

        locations.append(
            {
                "uri": uri,
                "position": {
                    "line": start["line"],
                    "character": start["character"],
                },
            }
        )

    return locations


def canonicalize_project_item(
    client: b3.LspClient,
    docs: DocumentManager,
    item: dict[str, Any],
) -> tuple[dict[str, Any], dict[str, Any]]:
    uri = item.get("uri")
    selection = item.get("selectionRange")
    name = item.get("name")

    if not isinstance(uri, str) or not isinstance(selection, dict):
        return item, {"method": "unchanged", "reason": "missing-location"}

    path = b3.path_from_file_uri(uri)
    if not is_rss_project_path(path):
        return item, {"method": "unchanged", "reason": "outside-rss-boundary"}

    start = selection.get("start")
    if not isinstance(start, dict):
        return item, {"method": "unchanged", "reason": "missing-selection-start"}

    docs.open_uri(uri)

    definition = client.request(
        "textDocument/definition",
        {
            "textDocument": {"uri": uri},
            "position": start,
        },
    )

    locations = definition_locations(definition.get("result"))
    local_locations = []
    for location in locations:
        location_path = b3.path_from_file_uri(location["uri"])
        if is_rss_project_path(location_path):
            local_locations.append(location)

    local_locations.sort(
        key=lambda location: (
            0
            if (
                (b3.path_from_file_uri(location["uri"]) or Path("x")).suffix.lower()
                in {".cpp", ".cc", ".cxx"}
            )
            else 1,
            project_path(b3.path_from_file_uri(location["uri"])) or "",
            location["position"]["line"],
            location["position"]["character"],
        )
    )

    for location in local_locations:
        prepared, error = prepare_at(
            client,
            docs,
            location["uri"],
            location["position"],
            str(name) if name is not None else None,
        )
        if prepared is not None:
            return prepared, {
                "method": "definition-then-prepare",
                "definition_uri": location["uri"],
                "prepare_error": error,
            }

    prepared, error = prepare_at(
        client,
        docs,
        uri,
        start,
        str(name) if name is not None else None,
    )
    if prepared is not None:
        return prepared, {
            "method": "prepare-original-item",
            "prepare_error": error,
        }

    return item, {
        "method": "unchanged",
        "reason": "semantic-canonicalization-failed",
        "definition_error": definition.get("error"),
        "prepare_error": error,
    }


def wait_for_background_index(
    client: b3.LspClient,
    root_uri: str,
    root_position: dict[str, int],
    timeout: float,
) -> dict[str, Any]:
    started = time.monotonic()
    while True:
        progress = b3.background_index_progress(client.progress_events)
        if progress["completed"]:
            return {
                **progress,
                "elapsed_seconds": round(time.monotonic() - started, 3),
                "timed_out": False,
            }

        if time.monotonic() - started >= timeout:
            return {
                **progress,
                "elapsed_seconds": round(time.monotonic() - started, 3),
                "timed_out": True,
            }

        # A normal request gives the LSP reader an opportunity to drain progress
        # notifications without adding a custom stdout reader/thread.
        client.request(
            "textDocument/prepareCallHierarchy",
            {
                "textDocument": {"uri": root_uri},
                "position": root_position,
            },
        )
        time.sleep(1.0)


def call_range_source_evidence(
    caller_item: dict[str, Any],
    ranges: list[dict[str, Any]],
) -> dict[str, Any]:
    uri = caller_item.get("uri")
    if not isinstance(uri, str):
        return {
            "mapping_status": "unavailable-no-caller-uri",
            "caller_item_uri": None,
            "source_lines": [],
        }

    path = b3.path_from_file_uri(uri)
    if path is None:
        return {
            "mapping_status": "unavailable-non-file-uri",
            "caller_item_uri": uri,
            "source_lines": [],
        }

    # clangd 20 canonicalizes many call-hierarchy items to declarations in
    # headers while its outgoing fromRanges come from the implementation body.
    # The LSP shape carries those ranges without their original URI. Mapping
    # them through a header URI can therefore produce plausible-but-wrong text.
    # Only render source lines when the caller item itself is a source TU.
    if path.suffix.lower() not in {".cpp", ".cc", ".cxx"}:
        return {
            "mapping_status": "unmapped-caller-item-is-canonical-declaration",
            "caller_item_uri": uri,
            "source_lines": [],
        }

    return {
        "mapping_status": "mapped-from-caller-item-uri",
        "caller_item_uri": uri,
        "source_lines": b3.source_lines_for_ranges(uri, ranges),
    }


def merge_ranges(
    existing: list[dict[str, Any]],
    incoming: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    seen = {json.dumps(item, sort_keys=True) for item in existing}
    merged = list(existing)
    for item in incoming:
        key = json.dumps(item, sort_keys=True)
        if key not in seen:
            merged.append(item)
            seen.add(key)
    return merged


def detect_cycles(
    root_id: str,
    adjacency: dict[str, list[str]],
) -> list[list[str]]:
    cycles: set[tuple[str, ...]] = set()

    def walk(node: str, path: list[str], positions: dict[str, int]) -> None:
        if node in positions:
            cycle = path[positions[node] :] + [node]
            core = cycle[:-1]
            if not core:
                return

            rotations = [
                tuple(core[index:] + core[:index])
                for index in range(len(core))
            ]
            normalized = min(rotations)
            cycles.add(normalized + (normalized[0],))
            return

        next_positions = dict(positions)
        next_positions[node] = len(path)
        next_path = path + [node]
        for child in adjacency.get(node, []):
            walk(child, next_path, next_positions)

    walk(root_id, [], {})
    return [list(cycle) for cycle in sorted(cycles)]


def render_tree(
    root_id: str,
    nodes: dict[str, dict[str, Any]],
    adjacency: dict[str, list[str]],
    max_depth: int,
) -> str:
    lines: list[str] = []
    rendered: set[str] = set()

    def label(node_id: str) -> str:
        node = nodes[node_id]
        location = node.get("project_path") or node.get("path") or "?"
        line = node.get("line")
        suffix = f":{line}" if isinstance(line, int) else ""
        detail = node.get("detail")
        name = detail or node.get("name") or node_id
        return f"{name}  [{location}{suffix}]"

    def walk(
        node_id: str,
        prefix: str,
        depth: int,
        path: set[str],
        is_last: bool | None,
    ) -> None:
        connector = ""
        if is_last is not None:
            connector = "└─ " if is_last else "├─ "

        marker = ""
        if node_id in path:
            marker = "  ↺ [cycle]"
        elif node_id in rendered:
            marker = "  ↪ [duplicate]"

        lines.append(prefix + connector + label(node_id) + marker)

        if marker:
            return

        rendered.add(node_id)
        if depth >= max_depth:
            if adjacency.get(node_id):
                child_prefix = prefix + ("   " if is_last else "│  ") if is_last is not None else prefix
                lines.append(child_prefix + "└─ … [depth limit]")
            return

        children = adjacency.get(node_id, [])
        next_path = set(path)
        next_path.add(node_id)

        for index, child in enumerate(children):
            last = index == len(children) - 1
            child_prefix = prefix
            if is_last is not None:
                child_prefix += "   " if is_last else "│  "
            walk(child, child_prefix, depth + 1, next_path, last)

    walk(root_id, "", 0, set(), None)
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "B4 probe: recursively build a manually rooted RSS2 call map from "
            "clangd 20 outgoing call hierarchy."
        )
    )
    parser.add_argument(
        "--root-id",
        default="rss-data-manager-tick",
        help="Root id from b4_manual_roots.json.",
    )
    parser.add_argument(
        "--clangd",
        help=(
            "Explicit clangd 20 executable. The WinGet LLVM.clangd 20.1.8 "
            "portable alias is recommended."
        ),
    )
    parser.add_argument(
        "--max-depth",
        type=int,
        default=4,
        help="Maximum RSS2 call depth to expand from the root.",
    )
    parser.add_argument(
        "--max-nodes",
        type=int,
        default=80,
        help="Safety cap for unique RSS2 nodes.",
    )
    parser.add_argument(
        "--index-timeout",
        type=float,
        default=180.0,
        help="Maximum seconds to wait for RSS2 background indexing.",
    )
    args = parser.parse_args()

    if args.max_depth < 1:
        raise RuntimeError("--max-depth must be at least 1")
    if args.max_nodes < 2:
        raise RuntimeError("--max-nodes must be at least 2")

    repo = b3.repository_root()
    root_spec = load_root(args.root_id)

    sml_value = os.environ.get("SML_PROJECT_ROOT") or b3.read_dotenv_value(
        repo / ".env", "SML_PROJECT_ROOT"
    )
    if not sml_value:
        raise RuntimeError(
            "SML_PROJECT_ROOT is not set in the environment or repository .env"
        )

    sml_root = b3.resolve_configured_path(repo, sml_value)

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
            "B4 is pinned to the B3-proven clangd 20 semantic stack. "
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
    rss_entries = [entry for entry in entries if b3.is_rss_translation_unit(entry)]
    if not rss_entries:
        raise RuntimeError("No RSS2 translation units found in B2 Clang database")

    output_dir = repo / "work" / "external-call-map" / "b4"
    compile_dir = output_dir / "rss-compile-db-clangd-20"
    compile_database = compile_dir / "compile_commands.json"
    summary_path = output_dir / "b4-manual-call-map.json"
    tree_path = output_dir / "b4-manual-call-tree.txt"
    stderr_log = output_dir / "clangd-20-b4-stderr.log"
    output_dir.mkdir(parents=True, exist_ok=True)
    compile_dir.mkdir(parents=True, exist_ok=True)

    compile_database.write_text(
        json.dumps(rss_entries, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    root_source = sml_root / str(root_spec["source"])
    if not root_source.is_file():
        raise RuntimeError(f"B4 root source file not found: {root_source}")

    root_text = root_source.read_text(encoding="utf-8-sig")
    root_position = b3.lsp_position(
        root_text,
        str(root_spec["needle"]),
        str(root_spec["symbol_token"]),
    )

    print(f"Repository:       {repo}")
    print(f"SML project:      {sml_root}")
    print(f"clangd:           {clangd}")
    print(f"Source database:  {source_database}")
    print(f"RSS2 TUs:         {len(rss_entries)}")
    print(f"Manual root:      {root_spec['symbol']}")
    print(f"Framework owner:  {root_spec['external_owner']}")
    print(f"Max depth:        {args.max_depth}")
    print(f"Max nodes:        {args.max_nodes}")
    print()
    print(clangd_version)

    client = b3.LspClient(clangd, compile_dir, sml_root, stderr_log)
    docs = DocumentManager(client)
    started = time.monotonic()

    nodes: dict[str, dict[str, Any]] = {}
    node_items: dict[str, dict[str, Any]] = {}
    key_to_id: dict[str, str] = {}
    edges_by_pair: dict[tuple[str, str], dict[str, Any]] = {}
    boundary_calls: list[dict[str, Any]] = []
    expansion_errors: list[dict[str, Any]] = []
    duplicate_node_references = 0
    truncated_by_node_cap = False

    try:
        docs.open_uri(root_source.as_uri())

        root_prepare = client.request(
            "textDocument/prepareCallHierarchy",
            {
                "textDocument": {"uri": root_source.as_uri()},
                "position": root_position,
            },
        )
        if root_prepare.get("error") is not None:
            raise RuntimeError(
                f"B4 root prepareCallHierarchy failed: {root_prepare['error']}"
            )

        root_item = choose_prepared_item(
            root_prepare.get("result"),
            str(root_spec["symbol"]),
        )
        if root_item is None:
            # clangd commonly returns the fully-qualified symbol while the
            # manual root metadata is also fully qualified. If exact matching
            # did not work, accept the first prepared item rather than parsing
            # source text.
            prepared = root_prepare.get("result")
            if isinstance(prepared, list) and prepared and isinstance(prepared[0], dict):
                root_item = prepared[0]
            else:
                raise RuntimeError(
                    "clangd returned no call-hierarchy item for the B4 root"
                )

        index_progress = wait_for_background_index(
            client,
            root_source.as_uri(),
            root_position,
            args.index_timeout,
        )

        root_item, root_canonicalization = canonicalize_project_item(
            client, docs, root_item
        )

        root_key = item_key(root_item)
        root_id = "n0001"
        key_to_id[root_key] = root_id
        node_items[root_id] = root_item

        root_compact = compact_node_item(root_item)
        nodes[root_id] = {
            "id": root_id,
            **root_compact,
            "depth": 0,
            "root": True,
            "canonicalization": root_canonicalization,
            "expansion_status": "pending",
            "local_outgoing_count": 0,
            "boundary_outgoing_count": 0,
        }

        queue: list[tuple[str, int]] = [(root_id, 0)]
        expanded: set[str] = set()

        while queue:
            source_id, depth = queue.pop(0)
            if source_id in expanded:
                continue

            source_item = node_items[source_id]
            source_uri = source_item.get("uri")
            if not isinstance(source_uri, str):
                nodes[source_id]["expansion_status"] = "missing-uri"
                expanded.add(source_id)
                continue

            if depth >= args.max_depth:
                nodes[source_id]["expansion_status"] = "depth-limit"
                expanded.add(source_id)
                continue

            response = client.request(
                "callHierarchy/outgoingCalls",
                {"item": source_item},
            )
            if response.get("error") is not None:
                nodes[source_id]["expansion_status"] = "lsp-error"
                expansion_errors.append(
                    {
                        "node_id": source_id,
                        "error": response.get("error"),
                    }
                )
                expanded.add(source_id)
                continue

            raw_calls = response.get("result")
            if not isinstance(raw_calls, list):
                raw_calls = []

            sortable: list[tuple[dict[str, Any], list[dict[str, Any]]]] = []
            for call in raw_calls:
                if not isinstance(call, dict):
                    continue
                target = call.get("to")
                ranges = call.get("fromRanges")
                if not isinstance(target, dict):
                    continue
                if not isinstance(ranges, list):
                    ranges = []
                sortable.append((target, ranges))

            sortable.sort(key=lambda pair: item_sort_key(pair[0]))

            local_count = 0
            boundary_count = 0

            for raw_target, call_ranges in sortable:
                target_uri = raw_target.get("uri")
                target_path = (
                    b3.path_from_file_uri(target_uri)
                    if isinstance(target_uri, str)
                    else None
                )

                call_ranges = merge_ranges([], call_ranges)
                call_evidence = call_range_source_evidence(
                    source_item, call_ranges
                )
                source_lines = call_evidence["source_lines"]

                if not is_rss_project_path(target_path):
                    boundary_count += 1
                    boundary_calls.append(
                        {
                            "from": source_id,
                            "callee": compact_node_item(raw_target),
                            "call_ranges": call_ranges,
                            "source_lines": source_lines,
                            "source_line_mapping": {
                                "status": call_evidence["mapping_status"],
                                "caller_item_uri": call_evidence["caller_item_uri"],
                            },
                        }
                    )
                    continue

                local_count += 1
                target_item, canonicalization = canonicalize_project_item(
                    client, docs, raw_target
                )
                target_key = item_key(target_item)

                target_id = key_to_id.get(target_key)
                if target_id is None:
                    if len(nodes) >= args.max_nodes:
                        truncated_by_node_cap = True
                        continue

                    target_id = f"n{len(nodes) + 1:04d}"
                    key_to_id[target_key] = target_id
                    node_items[target_id] = target_item
                    target_compact = compact_node_item(target_item)
                    nodes[target_id] = {
                        "id": target_id,
                        **target_compact,
                        "depth": depth + 1,
                        "root": False,
                        "canonicalization": canonicalization,
                        "expansion_status": "pending",
                        "local_outgoing_count": 0,
                        "boundary_outgoing_count": 0,
                    }
                    queue.append((target_id, depth + 1))
                else:
                    duplicate_node_references += 1
                    nodes[target_id]["depth"] = min(
                        int(nodes[target_id]["depth"]),
                        depth + 1,
                    )

                pair_key = (source_id, target_id)
                existing = edges_by_pair.get(pair_key)
                if existing is None:
                    edges_by_pair[pair_key] = {
                        "from": source_id,
                        "to": target_id,
                        "call_ranges": list(call_ranges),
                        "source_lines": list(source_lines),
                        "source_line_mapping": {
                            "status": call_evidence["mapping_status"],
                            "caller_item_uri": call_evidence["caller_item_uri"],
                        },
                    }
                else:
                    existing["call_ranges"] = merge_ranges(
                        existing["call_ranges"], call_ranges
                    )
                    existing["source_lines"] = merge_ranges(
                        existing["source_lines"], source_lines
                    )

            nodes[source_id]["local_outgoing_count"] = local_count
            nodes[source_id]["boundary_outgoing_count"] = boundary_count
            nodes[source_id]["expansion_status"] = "expanded"
            expanded.add(source_id)

        edges = sorted(
            edges_by_pair.values(),
            key=lambda edge: (edge["from"], edge["to"]),
        )

        adjacency: dict[str, list[str]] = defaultdict(list)
        for edge in edges:
            if edge["to"] not in adjacency[edge["from"]]:
                adjacency[edge["from"]].append(edge["to"])

        for source_id in adjacency:
            adjacency[source_id].sort(
                key=lambda node_id: (
                    str(nodes[node_id].get("project_path") or "").lower(),
                    int(nodes[node_id].get("line") or 10**9),
                    str(nodes[node_id].get("name") or ""),
                )
            )

        cycles = detect_cycles(root_id, dict(adjacency))
        cross_file_edges = [
            edge
            for edge in edges
            if nodes[edge["from"]].get("project_path")
            != nodes[edge["to"]].get("project_path")
        ]
        max_depth_reached = max(
            (int(node["depth"]) for node in nodes.values()),
            default=0,
        )

        tree = render_tree(
            root_id,
            nodes,
            dict(adjacency),
            args.max_depth,
        )

        index_log_summary = b3.summarize_index_log(stderr_log)

        pass_condition = (
            len(nodes) >= 4
            and len(edges) >= 3
            and max_depth_reached >= 2
            and len(cross_file_edges) >= 1
            and not any(error["node_id"] == root_id for error in expansion_errors)
        )

        summary = {
            "generated_at": datetime.now().astimezone().isoformat(),
            "status": "manual-call-map-completed",
            "clangd": str(clangd),
            "clangd_version": clangd_version,
            "clangd_major": clangd_major,
            "source_database": str(source_database),
            "filtered_database": str(compile_database),
            "filtered_rss2_entries": len(rss_entries),
            "root_spec_file": str(ROOTS_FILE),
            "manual_root": root_spec,
            "root_id": root_id,
            "limits": {
                "max_depth": args.max_depth,
                "max_nodes": args.max_nodes,
                "index_timeout_seconds": args.index_timeout,
            },
            "background_index_progress": index_progress,
            "background_index_log_summary": index_log_summary,
            "metrics": {
                "node_count": len(nodes),
                "edge_count": len(edges),
                "cross_file_edge_count": len(cross_file_edges),
                "boundary_call_count": len(boundary_calls),
                "duplicate_node_references": duplicate_node_references,
                "cycle_count": len(cycles),
                "max_depth_reached": max_depth_reached,
                "truncated_by_node_cap": truncated_by_node_cap,
                "elapsed_seconds": round(time.monotonic() - started, 3),
            },
            "nodes": sorted(
                nodes.values(),
                key=lambda node: node["id"],
            ),
            "edges": edges,
            "cycles": cycles,
            "boundary_calls": boundary_calls,
            "expansion_errors": expansion_errors,
            "b4_pass_condition": pass_condition,
            "outputs": {
                "json": str(summary_path),
                "tree": str(tree_path),
                "clangd_log": str(stderr_log),
            },
        }

        tree_header = [
            "B4 manual RSS2 call tree",
            f"Root: {root_spec['symbol']}",
            f"Framework entry: {root_spec['framework_entry_kind']}",
            f"External owner: {root_spec['external_owner']}",
            f"clangd: {clangd_version.splitlines()[0]}",
            (
                "Metrics: "
                f"{len(nodes)} nodes, {len(edges)} RSS2 edges, "
                f"{len(cross_file_edges)} cross-file edges, "
                f"{duplicate_node_references} duplicate references, "
                f"{len(cycles)} cycles"
            ),
            "",
        ]
        tree_text = "\n".join(tree_header) + tree + "\n"

        summary_path.write_text(
            json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        tree_path.write_text(tree_text, encoding="utf-8")

        print()
        print(tree_text)
        print(f"B4 pass condition: {pass_condition}")
        print(f"JSON: {summary_path}")
        print(f"Tree: {tree_path}")
        print(f"Log:  {stderr_log}")

        return 0 if pass_condition else 2
    finally:
        docs.close_all()
        client.close()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
