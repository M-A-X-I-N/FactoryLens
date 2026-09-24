#!/usr/bin/env python3
from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import datetime
import json
import os
from pathlib import Path
import shutil
import statistics
import subprocess
import sys
import time
from typing import Any

import b3_probe_call_hierarchy as b3
import b4_build_manual_call_map as b4


ROOTS_FILE = Path(__file__).with_name("b5_manual_roots.json")


def load_roots(selected_ids: list[str] | None) -> list[dict[str, Any]]:
    parsed = json.loads(ROOTS_FILE.read_text(encoding="utf-8"))
    roots = parsed.get("roots")
    if not isinstance(roots, list):
        raise RuntimeError(f"Invalid B5 roots file: {ROOTS_FILE}")

    valid = [root for root in roots if isinstance(root, dict)]
    if not selected_ids:
        return valid

    by_id = {
        str(root.get("id")): root
        for root in valid
        if isinstance(root.get("id"), str)
    }
    missing = [root_id for root_id in selected_ids if root_id not in by_id]
    if missing:
        raise RuntimeError(
            "Unknown B5 root id(s): "
            + ", ".join(missing)
            + ". Available: "
            + ", ".join(sorted(by_id))
        )

    return [by_id[root_id] for root_id in selected_ids]


def root_source_and_position(
    sml_root: Path,
    root_spec: dict[str, Any],
) -> tuple[Path, dict[str, int]]:
    source = sml_root / str(root_spec["source"])
    if not source.is_file():
        raise RuntimeError(f"B5 root source file not found: {source}")

    text = source.read_text(encoding="utf-8-sig", errors="replace")
    position = b3.lsp_position(
        text,
        str(root_spec["needle"]),
        str(root_spec["symbol_token"]),
    )
    return source, position


def prepare_root_item(
    client: b3.LspClient,
    docs: b4.DocumentManager,
    source: Path,
    position: dict[str, int],
    root_spec: dict[str, Any],
) -> tuple[dict[str, Any] | None, Any]:
    docs.open_uri(source.as_uri())
    response = client.request(
        "textDocument/prepareCallHierarchy",
        {
            "textDocument": {"uri": source.as_uri()},
            "position": position,
        },
    )
    if response.get("error") is not None:
        return None, response.get("error")

    item = b4.choose_prepared_item(
        response.get("result"),
        str(root_spec.get("symbol_token") or ""),
    )
    if item is None:
        prepared = response.get("result")
        if (
            isinstance(prepared, list)
            and prepared
            and isinstance(prepared[0], dict)
        ):
            item = prepared[0]

    return item, None


def serialize_node(
    node_id: str,
    item: dict[str, Any],
    *,
    depth: int,
    root: bool,
    canonicalization: dict[str, Any],
) -> dict[str, Any]:
    return {
        "id": node_id,
        **b4.compact_node_item(item),
        "semantic_key": b4.item_key(item),
        "depth": depth,
        "root": root,
        "canonicalization": canonicalization,
        "expansion_status": "pending",
        "local_outgoing_count": 0,
        "boundary_outgoing_count": 0,
    }


def traverse_root(
    *,
    client: b3.LspClient,
    docs: b4.DocumentManager,
    root_spec: dict[str, Any],
    root_item: dict[str, Any],
    max_depth: int,
    max_nodes: int,
) -> dict[str, Any]:
    started = time.monotonic()

    root_item, root_canonicalization = b4.canonicalize_project_item(
        client, docs, root_item
    )

    nodes: dict[str, dict[str, Any]] = {}
    node_items: dict[str, dict[str, Any]] = {}
    key_to_id: dict[str, str] = {}
    edges_by_pair: dict[tuple[str, str], dict[str, Any]] = {}
    boundary_calls: list[dict[str, Any]] = []
    expansion_errors: list[dict[str, Any]] = []
    duplicate_node_references = 0
    truncated_by_node_cap = False
    root_direct_project_callee_names: set[str] = set()

    root_key = b4.item_key(root_item)
    root_id = "n0001"
    key_to_id[root_key] = root_id
    node_items[root_id] = root_item
    nodes[root_id] = serialize_node(
        root_id,
        root_item,
        depth=0,
        root=True,
        canonicalization=root_canonicalization,
    )

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

        if depth >= max_depth:
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

        sortable.sort(key=lambda pair: b4.item_sort_key(pair[0]))

        local_count = 0
        boundary_count = 0

        for raw_target, raw_ranges in sortable:
            call_ranges = b4.merge_ranges([], raw_ranges)
            target_uri = raw_target.get("uri")
            target_path = (
                b3.path_from_file_uri(target_uri)
                if isinstance(target_uri, str)
                else None
            )

            call_evidence = b4.call_range_source_evidence(
                source_item, call_ranges
            )
            source_lines = call_evidence["source_lines"]

            if not b4.is_rss_project_path(target_path):
                boundary_count += 1
                boundary_calls.append(
                    {
                        "from": source_id,
                        "callee": b4.compact_node_item(raw_target),
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
            if source_id == root_id:
                name = raw_target.get("name")
                if isinstance(name, str):
                    root_direct_project_callee_names.add(name)

            target_item, canonicalization = b4.canonicalize_project_item(
                client, docs, raw_target
            )
            target_key = b4.item_key(target_item)

            target_id = key_to_id.get(target_key)
            if target_id is None:
                if len(nodes) >= max_nodes:
                    truncated_by_node_cap = True
                    continue

                target_id = f"n{len(nodes) + 1:04d}"
                key_to_id[target_key] = target_id
                node_items[target_id] = target_item
                nodes[target_id] = serialize_node(
                    target_id,
                    target_item,
                    depth=depth + 1,
                    root=False,
                    canonicalization=canonicalization,
                )
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
                existing["call_ranges"] = b4.merge_ranges(
                    existing["call_ranges"], call_ranges
                )
                existing["source_lines"] = b4.merge_ranges(
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

    cycles = b4.detect_cycles(root_id, dict(adjacency))
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

    depth_histogram: dict[str, int] = {}
    for node in nodes.values():
        key = str(int(node["depth"]))
        depth_histogram[key] = depth_histogram.get(key, 0) + 1

    expected = [
        str(name)
        for name in root_spec.get(
            "expected_direct_project_callees_smoke", []
        )
    ]
    matched_expected = [
        name for name in expected if name in root_direct_project_callee_names
    ]
    missing_expected = [
        name for name in expected if name not in root_direct_project_callee_names
    ]

    elapsed = time.monotonic() - started
    project_edge_count = len(edges)
    boundary_count = len(boundary_calls)
    noise_ratio = (
        round(boundary_count / project_edge_count, 3)
        if project_edge_count
        else None
    )

    return {
        "root_spec": root_spec,
        "root_id": root_id,
        "root_semantic_key": root_key,
        "elapsed_seconds": round(elapsed, 3),
        "metrics": {
            "node_count": len(nodes),
            "edge_count": len(edges),
            "cross_file_edge_count": len(cross_file_edges),
            "boundary_call_count": boundary_count,
            "boundary_calls_per_project_edge": noise_ratio,
            "duplicate_node_references": duplicate_node_references,
            "cycle_count": len(cycles),
            "max_depth_reached": max_depth_reached,
            "truncated_by_node_cap": truncated_by_node_cap,
            "expansion_error_count": len(expansion_errors),
            "depth_histogram": depth_histogram,
        },
        "direct_project_callee_names": sorted(
            root_direct_project_callee_names
        ),
        "expected_direct_project_callees_smoke": expected,
        "matched_expected_direct_callees": matched_expected,
        "missing_expected_direct_callees": missing_expected,
        "nodes": sorted(nodes.values(), key=lambda node: node["id"]),
        "edges": edges,
        "cycles": cycles,
        "boundary_calls": boundary_calls,
        "expansion_errors": expansion_errors,
        "_node_items": node_items,
    }


def semantic_union(
    root_results: list[dict[str, Any]],
) -> dict[str, Any]:
    union_nodes: dict[str, dict[str, Any]] = {}
    union_edges: dict[tuple[str, str], dict[str, Any]] = {}
    root_node_sets: dict[str, set[str]] = {}
    root_edge_sets: dict[str, set[tuple[str, str]]] = {}

    for result in root_results:
        root_name = str(result["root_spec"]["id"])
        id_to_key = {
            str(node["id"]): str(node["semantic_key"])
            for node in result["nodes"]
        }
        node_set = set(id_to_key.values())
        edge_set: set[tuple[str, str]] = set()

        for node in result["nodes"]:
            key = str(node["semantic_key"])
            current = union_nodes.get(key)
            if current is None:
                union_nodes[key] = {
                    **node,
                    "reachable_from_roots": [root_name],
                    "min_depth": int(node["depth"]),
                }
            else:
                if root_name not in current["reachable_from_roots"]:
                    current["reachable_from_roots"].append(root_name)
                current["min_depth"] = min(
                    int(current["min_depth"]),
                    int(node["depth"]),
                )

        for edge in result["edges"]:
            source_key = id_to_key[str(edge["from"])]
            target_key = id_to_key[str(edge["to"])]
            pair = (source_key, target_key)
            edge_set.add(pair)
            current = union_edges.get(pair)
            if current is None:
                union_edges[pair] = {
                    "from_semantic_key": source_key,
                    "to_semantic_key": target_key,
                    "reachable_from_roots": [root_name],
                }
            elif root_name not in current["reachable_from_roots"]:
                current["reachable_from_roots"].append(root_name)

        root_node_sets[root_name] = node_set
        root_edge_sets[root_name] = edge_set

    pairwise_overlap: list[dict[str, Any]] = []
    root_names = list(root_node_sets)
    for i, first in enumerate(root_names):
        for second in root_names[i + 1 :]:
            shared_nodes = root_node_sets[first] & root_node_sets[second]
            shared_edges = root_edge_sets[first] & root_edge_sets[second]
            pairwise_overlap.append(
                {
                    "root_a": first,
                    "root_b": second,
                    "shared_node_count": len(shared_nodes),
                    "shared_edge_count": len(shared_edges),
                }
            )

    adjacency: dict[str, list[str]] = defaultdict(list)
    for source_key, target_key in union_edges:
        if target_key not in adjacency[source_key]:
            adjacency[source_key].append(target_key)

    # Detect cycles from every root because the merged graph is a forest/union,
    # not a single rooted tree.
    union_cycles: set[tuple[str, ...]] = set()

    def walk(
        node: str,
        path: list[str],
        positions: dict[str, int],
    ) -> None:
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
            union_cycles.add(normalized + (normalized[0],))
            return

        next_positions = dict(positions)
        next_positions[node] = len(path)
        next_path = path + [node]
        for child in adjacency.get(node, []):
            walk(child, next_path, next_positions)

    for result in root_results:
        walk(str(result["root_semantic_key"]), [], {})

    per_root_node_sum = sum(len(value) for value in root_node_sets.values())
    per_root_edge_sum = sum(len(value) for value in root_edge_sets.values())

    unique_files = sorted(
        {
            str(node.get("project_path"))
            for node in union_nodes.values()
            if node.get("project_path")
        }
    )

    return {
        "metrics": {
            "unique_node_count": len(union_nodes),
            "unique_edge_count": len(union_edges),
            "unique_project_declaration_file_count": len(unique_files),
            "cross_root_node_overlap_references": (
                per_root_node_sum - len(union_nodes)
            ),
            "cross_root_edge_overlap_references": (
                per_root_edge_sum - len(union_edges)
            ),
            "cycle_count": len(union_cycles),
        },
        "unique_project_declaration_files": unique_files,
        "pairwise_root_overlap": pairwise_overlap,
        "cycles": [list(cycle) for cycle in sorted(union_cycles)],
        "nodes": sorted(
            union_nodes.values(),
            key=lambda node: (
                str(node.get("project_path") or ""),
                int(node.get("line") or 10**9),
                str(node.get("name") or ""),
            ),
        ),
        "edges": sorted(
            union_edges.values(),
            key=lambda edge: (
                edge["from_semantic_key"],
                edge["to_semantic_key"],
            ),
        ),
    }


def projection_metrics(
    root_results: list[dict[str, Any]],
    depths: list[int],
) -> list[dict[str, Any]]:
    projections: list[dict[str, Any]] = []
    for depth_limit in depths:
        total_nodes = 0
        total_edges = 0
        max_root_nodes = 0
        roots: list[dict[str, Any]] = []

        for result in root_results:
            allowed_ids = {
                str(node["id"])
                for node in result["nodes"]
                if int(node["depth"]) <= depth_limit
            }
            edge_count = sum(
                1
                for edge in result["edges"]
                if str(edge["from"]) in allowed_ids
                and str(edge["to"]) in allowed_ids
            )
            node_count = len(allowed_ids)
            total_nodes += node_count
            total_edges += edge_count
            max_root_nodes = max(max_root_nodes, node_count)
            roots.append(
                {
                    "root_id": result["root_spec"]["id"],
                    "node_count": node_count,
                    "edge_count": edge_count,
                }
            )

        projections.append(
            {
                "depth_limit": depth_limit,
                "sum_root_nodes_before_cross_root_dedup": total_nodes,
                "sum_root_edges_before_cross_root_dedup": total_edges,
                "largest_root_node_count": max_root_nodes,
                "roots": roots,
            }
        )

    return projections


def render_forest(
    root_results: list[dict[str, Any]],
    display_depth: int,
    clangd_version: str,
    timings: dict[str, Any],
) -> str:
    lines = [
        "B5 RSS2 scale-test forest",
        f"clangd: {clangd_version.splitlines()[0]}",
        f"roots: {len(root_results)}",
        f"display depth: {display_depth}",
        (
            "timings: setup/index "
            f"{timings['setup_through_index_seconds']:.3f}s, "
            "root prepare "
            f"{timings['total_root_prepare_seconds']:.3f}s, "
            "traversal "
            f"{timings['total_traversal_seconds']:.3f}s, "
            "total "
            f"{timings['total_elapsed_seconds']:.3f}s"
        ),
        "",
    ]

    for result in root_results:
        nodes = {
            str(node["id"]): node
            for node in result["nodes"]
        }
        adjacency: dict[str, list[str]] = defaultdict(list)
        for edge in result["edges"]:
            source = str(edge["from"])
            target = str(edge["to"])
            if target not in adjacency[source]:
                adjacency[source].append(target)

        for source in adjacency:
            adjacency[source].sort(
                key=lambda node_id: (
                    str(nodes[node_id].get("project_path") or ""),
                    int(nodes[node_id].get("line") or 10**9),
                    str(nodes[node_id].get("name") or ""),
                )
            )

        metrics = result["metrics"]
        lines.extend(
            [
                f"=== {result['root_spec']['symbol']} ===",
                (
                    f"{metrics['node_count']} nodes, "
                    f"{metrics['edge_count']} RSS2 edges, "
                    f"{metrics['boundary_call_count']} boundary calls, "
                    f"depth {metrics['max_depth_reached']}, "
                    f"{result['elapsed_seconds']:.3f}s"
                ),
            ]
        )

        missing = result["missing_expected_direct_callees"]
        if missing:
            lines.append(
                "expected-direct smoke misses: " + ", ".join(missing)
            )

        lines.append(
            b4.render_tree(
                str(result["root_id"]),
                nodes,
                dict(adjacency),
                display_depth,
            )
        )
        lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def count_index_shards(cache_dir: Path) -> int:
    if not cache_dir.exists():
        return 0
    return sum(1 for path in cache_dir.rglob("*.idx") if path.is_file())


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "B5 scale probe: traverse a small manually confirmed RSS2 root set "
            "inside one clangd 20 session, then measure per-root and merged "
            "graph size, overlap, timing, boundary noise, and depth pressure."
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
        "--root-id",
        action="append",
        dest="root_ids",
        help=(
            "Optional B5 root id to include. Repeat for multiple roots. "
            "Default: all roots from b5_manual_roots.json."
        ),
    )
    parser.add_argument(
        "--max-depth",
        type=int,
        default=6,
        help="Maximum RSS2 call depth to expand for each root.",
    )
    parser.add_argument(
        "--max-nodes-per-root",
        type=int,
        default=300,
        help="Safety cap for unique RSS2 nodes reached from each root.",
    )
    parser.add_argument(
        "--display-depth",
        type=int,
        default=3,
        help="Depth of the human-readable forest projection.",
    )
    parser.add_argument(
        "--index-timeout",
        type=float,
        default=240.0,
        help="Maximum seconds to wait for the RSS2 background index.",
    )
    parser.add_argument(
        "--reuse-index",
        action="store_true",
        help=(
            "Reuse the B5 clangd index cache instead of clearing it. "
            "The default cold run is preferred for the first B5 measurement."
        ),
    )
    args = parser.parse_args()

    if args.max_depth < 1:
        raise RuntimeError("--max-depth must be at least 1")
    if args.max_nodes_per_root < 2:
        raise RuntimeError("--max-nodes-per-root must be at least 2")
    if args.display_depth < 1:
        raise RuntimeError("--display-depth must be at least 1")

    roots = load_roots(args.root_ids)
    if len(roots) < 2:
        raise RuntimeError("B5 requires at least two manual roots")

    repo = b3.repository_root()
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
            "B5 is pinned to the B3/B4-proven clangd 20 semantic stack. "
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

    output_dir = repo / "work" / "external-call-map" / "b5"
    compile_dir = output_dir / "rss-compile-db-clangd-20"
    compile_database = compile_dir / "compile_commands.json"
    cache_dir = compile_dir / ".cache"
    summary_path = output_dir / "b5-scale-summary.json"
    forest_path = output_dir / "b5-scale-forest.txt"
    stderr_log = output_dir / "clangd-20-b5-stderr.log"
    output_dir.mkdir(parents=True, exist_ok=True)
    compile_dir.mkdir(parents=True, exist_ok=True)

    total_started = time.monotonic()
    database_started = time.monotonic()
    entries = b3.read_compilation_database(source_database)
    rss_entries = [
        entry for entry in entries if b3.is_rss_translation_unit(entry)
    ]
    if not rss_entries:
        raise RuntimeError("No RSS2 translation units found in B2 Clang database")

    compile_database.write_text(
        json.dumps(rss_entries, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    database_prepare_seconds = time.monotonic() - database_started

    cache_existed_before = cache_dir.exists()
    shards_before_clear = count_index_shards(cache_dir)
    cache_cleared = False
    if not args.reuse_index and cache_dir.exists():
        shutil.rmtree(cache_dir)
        cache_cleared = True
    shards_at_start = count_index_shards(cache_dir)

    root_locations = [
        (root, *root_source_and_position(sml_root, root))
        for root in roots
    ]

    print(f"Repository:       {repo}")
    print(f"SML project:      {sml_root}")
    print(f"clangd:           {clangd}")
    print(f"RSS2 TUs:         {len(rss_entries)}")
    print(f"B5 roots:         {len(roots)}")
    print(f"Max depth:        {args.max_depth}")
    print(f"Max nodes/root:   {args.max_nodes_per_root}")
    print(f"Display depth:    {args.display_depth}")
    print(f"Cold index:       {not args.reuse_index}")
    print()
    for root in roots:
        print(f"  - {root['symbol']}")
    print()
    print(clangd_version)

    initialize_started = time.monotonic()
    client = b3.LspClient(clangd, compile_dir, sml_root, stderr_log)
    clangd_initialize_seconds = time.monotonic() - initialize_started
    docs = b4.DocumentManager(client)

    root_results: list[dict[str, Any]] = []

    try:
        first_root, first_source, first_position = root_locations[0]
        docs.open_uri(first_source.as_uri())

        index_wait_started = time.monotonic()
        index_progress = b4.wait_for_background_index(
            client,
            first_source.as_uri(),
            first_position,
            args.index_timeout,
        )
        background_index_wait_seconds = (
            time.monotonic() - index_wait_started
        )
        setup_through_index_seconds = time.monotonic() - total_started

        for root_spec, source, position in root_locations:
            root_started = time.monotonic()
            root_item, prepare_error = prepare_root_item(
                client,
                docs,
                source,
                position,
                root_spec,
            )
            prepare_seconds = time.monotonic() - root_started
            if root_item is None:
                root_results.append(
                    {
                        "root_spec": root_spec,
                        "prepare_error": prepare_error
                        or "no call-hierarchy item returned",
                        "prepare_seconds": round(prepare_seconds, 3),
                        "elapsed_seconds": 0.0,
                        "metrics": {
                            "node_count": 0,
                            "edge_count": 0,
                            "cross_file_edge_count": 0,
                            "boundary_call_count": 0,
                            "boundary_calls_per_project_edge": None,
                            "duplicate_node_references": 0,
                            "cycle_count": 0,
                            "max_depth_reached": 0,
                            "truncated_by_node_cap": False,
                            "expansion_error_count": 0,
                            "depth_histogram": {},
                        },
                        "direct_project_callee_names": [],
                        "expected_direct_project_callees_smoke": (
                            root_spec.get(
                                "expected_direct_project_callees_smoke",
                                [],
                            )
                        ),
                        "matched_expected_direct_callees": [],
                        "missing_expected_direct_callees": (
                            root_spec.get(
                                "expected_direct_project_callees_smoke",
                                [],
                            )
                        ),
                        "nodes": [],
                        "edges": [],
                        "cycles": [],
                        "boundary_calls": [],
                        "expansion_errors": [],
                    }
                )
                continue

            result = traverse_root(
                client=client,
                docs=docs,
                root_spec=root_spec,
                root_item=root_item,
                max_depth=args.max_depth,
                max_nodes=args.max_nodes_per_root,
            )
            result["prepare_error"] = prepare_error
            result["prepare_seconds"] = round(prepare_seconds, 3)
            root_results.append(result)

            metrics = result["metrics"]
            print()
            print(f"==> {root_spec['symbol']}")
            print(
                f"    {metrics['node_count']} nodes, "
                f"{metrics['edge_count']} RSS2 edges, "
                f"{metrics['cross_file_edge_count']} cross-file, "
                f"{metrics['boundary_call_count']} boundary calls"
            )
            print(
                f"    depth {metrics['max_depth_reached']}, "
                f"duplicates {metrics['duplicate_node_references']}, "
                f"cycles {metrics['cycle_count']}, "
                f"prepare {result['prepare_seconds']:.3f}s, "
                f"traverse {result['elapsed_seconds']:.3f}s"
            )
            if result["missing_expected_direct_callees"]:
                print(
                    "    smoke misses: "
                    + ", ".join(
                        result["missing_expected_direct_callees"]
                    )
                )

        successful_results = [
            result
            for result in root_results
            if result.get("nodes")
        ]

        traversal_seconds = sum(
            float(result["elapsed_seconds"])
            for result in successful_results
        )
        prepare_seconds_total = sum(
            float(result.get("prepare_seconds") or 0.0)
            for result in root_results
        )
        post_index_query_seconds = (
            prepare_seconds_total + traversal_seconds
        )
        union = semantic_union(successful_results)
        projections = projection_metrics(
            successful_results,
            sorted(
                {
                    1,
                    2,
                    3,
                    min(args.max_depth, 4),
                    args.max_depth,
                }
            ),
        )

        per_root_times = [
            float(result["elapsed_seconds"])
            for result in successful_results
        ]
        timing_stats = {
            "database_prepare_seconds": round(
                database_prepare_seconds, 3
            ),
            "clangd_initialize_seconds": round(
                clangd_initialize_seconds, 3
            ),
            "background_index_wait_seconds": round(
                background_index_wait_seconds, 3
            ),
            "setup_through_index_seconds": round(
                setup_through_index_seconds, 3
            ),
            "total_root_prepare_seconds": round(
                prepare_seconds_total, 3
            ),
            "total_traversal_seconds": round(traversal_seconds, 3),
            "post_index_query_seconds": round(
                post_index_query_seconds, 3
            ),
            "mean_root_traversal_seconds": (
                round(statistics.mean(per_root_times), 3)
                if per_root_times
                else None
            ),
            "median_root_traversal_seconds": (
                round(statistics.median(per_root_times), 3)
                if per_root_times
                else None
            ),
            "max_root_traversal_seconds": (
                round(max(per_root_times), 3)
                if per_root_times
                else None
            ),
            "total_elapsed_seconds": round(
                time.monotonic() - total_started, 3
            ),
        }

        forest = render_forest(
            successful_results,
            args.display_depth,
            clangd_version,
            timing_stats,
        )
        forest_path.write_text(forest, encoding="utf-8")

        index_log_summary = b3.summarize_index_log(stderr_log)
        shards_after = count_index_shards(cache_dir)

        smoke_missing_total = sum(
            len(result["missing_expected_direct_callees"])
            for result in root_results
        )
        root_prepare_failures = [
            result["root_spec"]["id"]
            for result in root_results
            if result.get("prepare_error") is not None
            or not result.get("nodes")
        ]
        root_expansion_error_count = sum(
            int(result["metrics"]["expansion_error_count"])
            for result in root_results
        )
        truncated_roots = [
            result["root_spec"]["id"]
            for result in root_results
            if result["metrics"]["truncated_by_node_cap"]
        ]

        evidence_ready = (
            len(successful_results) >= 4
            and not root_prepare_failures
            and root_expansion_error_count == 0
            and union["metrics"]["unique_node_count"] >= 20
        )

        serializable_results = []
        for result in root_results:
            clean = {
                key: value
                for key, value in result.items()
                if not key.startswith("_")
            }
            serializable_results.append(clean)

        summary = {
            "generated_at": datetime.now().astimezone().isoformat(),
            "status": "scale-probe-completed",
            "clangd": str(clangd),
            "clangd_version": clangd_version,
            "clangd_major": clangd_major,
            "source_database": str(source_database),
            "filtered_database": str(compile_database),
            "filtered_rss2_entries": len(rss_entries),
            "root_spec_file": str(ROOTS_FILE),
            "root_count": len(roots),
            "limits": {
                "max_depth": args.max_depth,
                "max_nodes_per_root": args.max_nodes_per_root,
                "display_depth": args.display_depth,
                "index_timeout_seconds": args.index_timeout,
            },
            "index_cache": {
                "reuse_requested": args.reuse_index,
                "cache_existed_before": cache_existed_before,
                "cache_cleared_for_cold_run": cache_cleared,
                "shards_before_clear": shards_before_clear,
                "shards_at_start": shards_at_start,
                "shards_after": shards_after,
            },
            "background_index_progress": index_progress,
            "background_index_log_summary": index_log_summary,
            "timings": timing_stats,
            "roots": serializable_results,
            "union": union,
            "depth_projections": projections,
            "scale_observations": {
                "smoke_missing_direct_callee_count": smoke_missing_total,
                "root_prepare_failures": root_prepare_failures,
                "root_expansion_error_count": root_expansion_error_count,
                "truncated_roots": truncated_roots,
                "sum_boundary_calls": sum(
                    int(result["metrics"]["boundary_call_count"])
                    for result in root_results
                ),
                "note": (
                    "Operational practicality is intentionally a manual B5 "
                    "conclusion. This probe records timing, graph growth, "
                    "cross-root overlap, boundary noise, depth pressure, and "
                    "obvious smoke misses without hard-coding an arbitrary "
                    "human-navigability threshold."
                ),
            },
            "b5_evidence_ready": evidence_ready,
            "outputs": {
                "json": str(summary_path),
                "forest": str(forest_path),
                "clangd_log": str(stderr_log),
            },
        }

        summary_path.write_text(
            json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )

        print()
        print(f"B5 evidence ready: {evidence_ready}")
        print(
            "Union: "
            f"{union['metrics']['unique_node_count']} unique nodes, "
            f"{union['metrics']['unique_edge_count']} unique edges, "
            f"{union['metrics']['cross_root_node_overlap_references']} "
            "cross-root duplicate node references"
        )
        print(
            "Timing: "
            f"{timing_stats['setup_through_index_seconds']:.3f}s setup/index, "
            f"{timing_stats['total_root_prepare_seconds']:.3f}s root prepare, "
            f"{timing_stats['total_traversal_seconds']:.3f}s traversal, "
            f"{timing_stats['total_elapsed_seconds']:.3f}s total"
        )
        print(f"JSON:   {summary_path}")
        print(f"Forest: {forest_path}")
        print(f"Log:    {stderr_log}")

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
