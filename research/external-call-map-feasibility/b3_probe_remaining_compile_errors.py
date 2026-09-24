#!/usr/bin/env python3
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from typing import Any


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

    program_files = os.environ.get("ProgramFiles")
    candidates: list[Path] = []
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

    for candidate in candidates:
        checked.append(str(candidate))
        if candidate.is_file():
            return candidate.resolve(), checked

    return None, checked


def normalize_file(path: str | Path) -> str:
    return os.path.normcase(os.path.abspath(os.fspath(path)))


def read_database(path: Path) -> list[dict[str, Any]]:
    parsed = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(parsed, list):
        raise RuntimeError(f"Compilation database is not a JSON array: {path}")
    return parsed


def find_entry(
    entries: list[dict[str, Any]], source: Path
) -> dict[str, Any] | None:
    needle = normalize_file(source)
    for entry in entries:
        file_value = entry.get("file")
        if isinstance(file_value, str) and normalize_file(file_value) == needle:
            return entry
    return None


RSP_TOKEN = re.compile(r'@(?:"([^"]+)"|(\S+))')
RESOURCE_DIR_TOKEN = re.compile(
    r'(?:"-resource-dir=[^"]+"|-resource-dir="[^"]+"|-resource-dir=\S+)'
)
ERROR_LIMIT_TOKEN = re.compile(r"(?:-ferror-limit(?:=|\s+)\d+)")


def response_file_from_command(command: str) -> Path:
    match = RSP_TOKEN.search(command)
    if not match:
        raise RuntimeError(
            f"Compilation command does not contain a response file: {command}"
        )
    return Path(match.group(1) or match.group(2))


def rewrite_response_file(
    *,
    original: Path,
    destination: Path,
    error_limit: int,
) -> dict[str, Any]:
    text = original.read_text(encoding="utf-8-sig", errors="replace")

    removed_resource_dirs = [
        match.group(0) for match in RESOURCE_DIR_TOKEN.finditer(text)
    ]
    if not removed_resource_dirs:
        raise RuntimeError(
            f"No explicit -resource-dir token found in response file: {original}"
        )

    rewritten = RESOURCE_DIR_TOKEN.sub("", text)

    removed_error_limits = [
        match.group(0) for match in ERROR_LIMIT_TOKEN.finditer(rewritten)
    ]
    rewritten = ERROR_LIMIT_TOKEN.sub("", rewritten)

    # This is diagnostic-only. Raising the error cap does not attempt to make
    # the TU compile; it only allows clangd --check to expose the error families
    # that were previously hidden behind "too many errors emitted".
    rewritten = rewritten.rstrip() + f" -ferror-limit={error_limit}\n"

    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(rewritten, encoding="utf-8")

    return {
        "original_response_file": str(original),
        "rewritten_response_file": str(destination),
        "removed_resource_dir_tokens": removed_resource_dirs,
        "removed_existing_error_limit_tokens": removed_error_limits,
        "added_error_limit": error_limit,
    }


def rewrite_entry(
    *,
    entry: dict[str, Any],
    rsp_dir: Path,
    error_limit: int,
) -> tuple[dict[str, Any], dict[str, Any]]:
    command = entry.get("command")
    if not isinstance(command, str):
        raise RuntimeError(
            "Expected compile database entry with a command string."
        )

    original_rsp = response_file_from_command(command)
    if not original_rsp.is_file():
        raise RuntimeError(f"Response file not found: {original_rsp}")

    rewritten_rsp = rsp_dir / original_rsp.name
    metadata = rewrite_response_file(
        original=original_rsp,
        destination=rewritten_rsp,
        error_limit=error_limit,
    )

    match = RSP_TOKEN.search(command)
    assert match is not None
    rewritten_command = (
        command[: match.start()]
        + '@"'
        + str(rewritten_rsp)
        + '"'
        + command[match.end() :]
    )

    rewritten_entry = dict(entry)
    rewritten_entry["command"] = rewritten_command

    metadata.update(
        {
            "file": entry.get("file"),
            "compiler_prefix_unchanged": command.split("@", 1)[0].strip(),
        }
    )
    return rewritten_entry, metadata


DIAGNOSTIC_LINE = re.compile(
    r"^E\[[^\]]+\]\s+\[([^\]]+)\]\s+Line\s+[^:]+:\s*(.*)$"
)


def parse_diagnostics(lines: list[str]) -> tuple[list[str], Counter[str]]:
    diagnostics: list[str] = []
    counts: Counter[str] = Counter()

    for line in lines:
        match = DIAGNOSTIC_LINE.match(line)
        if not match:
            continue
        diagnostics.append(line)
        counts[match.group(1)] += 1

    return diagnostics, counts


def run_check(
    *,
    clangd: Path,
    database_dir: Path,
    source: Path,
    output_dir: Path,
) -> dict[str, Any]:
    log_path = output_dir / f"{source.stem}-remaining-errors.log"

    completed = subprocess.run(
        [
            str(clangd),
            f"--check={source}",
            f"--compile-commands-dir={database_dir}",
            "--log=verbose",
        ],
        capture_output=True,
        text=True,
        errors="replace",
        check=False,
    )

    output = (completed.stdout or "") + (completed.stderr or "")
    log_path.write_text(output, encoding="utf-8")
    lines = output.splitlines()

    diagnostics, counts = parse_diagnostics(lines)
    fatal_limit = [
        line for line in diagnostics if "[fatal_too_many_errors]" in line
    ]
    builtin_prefetch = [
        line
        for line in diagnostics
        if "_m_prefetch" in line and "builtin" in line.lower()
    ]

    reported_errors: int | None = None
    for line in lines:
        match = re.search(r"All checks completed,\s*(\d+)\s+errors?", line)
        if match:
            reported_errors = int(match.group(1))

    sorted_counts = [
        {"code": code, "count": count}
        for code, count in counts.most_common()
    ]

    print()
    print(f"==> {source.name}")
    print(f"    exit code:               {completed.returncode}")
    print(f"    reported check errors:   {reported_errors}")
    print(f"    parsed source diagnostics:{len(diagnostics):>4}")
    print(f"    hit error limit:          {bool(fatal_limit)}")
    print(f"    _m_prefetch failures:     {len(builtin_prefetch)}")
    print("    top diagnostic codes:")
    for item in sorted_counts[:12]:
        print(f"      {item['code']}: {item['count']}")
    print(f"    log: {log_path}")

    return {
        "file": str(source),
        "exit_code": completed.returncode,
        "reported_check_errors": reported_errors,
        "parsed_source_diagnostic_count": len(diagnostics),
        "diagnostic_code_counts": sorted_counts,
        "diagnostic_sample": diagnostics[:80],
        "hit_error_limit": bool(fatal_limit),
        "builtin_prefetch_diagnostics": builtin_prefetch,
        "log": str(log_path),
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "B3 diagnostic probe: preserve the proven resource-dir alignment "
            "and raise clang's error cap to expose remaining diagnostic families."
        )
    )
    parser.add_argument(
        "--clangd",
        help="Explicit path to clangd.exe; standalone LLVM is preferred.",
    )
    parser.add_argument(
        "--error-limit",
        type=int,
        default=100,
        help="Diagnostic error cap written into the copied response files.",
    )
    args = parser.parse_args()

    if args.error_limit < 1:
        raise RuntimeError("--error-limit must be at least 1")

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

    clangd_version = subprocess.run(
        [str(clangd), "--version"],
        capture_output=True,
        text=True,
        errors="replace",
        check=False,
    ).stdout.strip()

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

    sources = [
        (
            sml_root
            / "Mods"
            / "GameFeatures"
            / "RSS"
            / "Source"
            / "RSS"
            / "Private"
            / "RssBlueprintFunctionLibrary.cpp"
        ),
        (
            sml_root
            / "Mods"
            / "GameFeatures"
            / "RSS"
            / "Source"
            / "RSS"
            / "Private"
            / "Serialization"
            / "RssJsonSerializer.cpp"
        ),
        (
            sml_root
            / "Mods"
            / "GameFeatures"
            / "RSS"
            / "Source"
            / "RSS"
            / "Private"
            / "Widget"
            / "RssDownloadImage.cpp"
        ),
    ]

    entries = read_database(source_database)
    selected: list[tuple[Path, dict[str, Any]]] = []
    for source in sources:
        if not source.is_file():
            raise RuntimeError(f"Representative RSS2 file not found: {source}")
        entry = find_entry(entries, source)
        if entry is None:
            raise RuntimeError(
                f"Representative RSS2 file missing from database: {source}"
            )
        selected.append((source, entry))

    output_dir = (
        repo
        / "work"
        / "external-call-map"
        / "b3"
        / "remaining-compile-errors"
    )
    database_dir = output_dir / "compile-db"
    rsp_dir = database_dir / "response-files"
    summary_path = output_dir / "b3-remaining-compile-errors-summary.json"
    database_dir.mkdir(parents=True, exist_ok=True)

    rewritten_entries: list[dict[str, Any]] = []
    rewrite_metadata: list[dict[str, Any]] = []

    for _, entry in selected:
        rewritten, metadata = rewrite_entry(
            entry=entry,
            rsp_dir=rsp_dir,
            error_limit=args.error_limit,
        )
        rewritten_entries.append(rewritten)
        rewrite_metadata.append(metadata)

    derived_database = database_dir / "compile_commands.json"
    derived_database.write_text(
        json.dumps(rewritten_entries, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    print(f"Repository:       {repo}")
    print(f"SML project:      {sml_root}")
    print(f"clangd:           {clangd}")
    print(f"Source database:  {source_database}")
    print(f"Derived database: {derived_database}")
    print(f"Error limit:      {args.error_limit}")
    print()
    print(clangd_version)

    results = [
        run_check(
            clangd=clangd,
            database_dir=database_dir,
            source=source,
            output_dir=output_dir,
        )
        for source, _ in selected
    ]

    summary = {
        "generated_at": datetime.now().astimezone().isoformat(),
        "status": "remaining-compile-errors-completed",
        "clangd": str(clangd),
        "clangd_version": clangd_version,
        "source_database": str(source_database),
        "derived_database": str(derived_database),
        "error_limit": args.error_limit,
        "transformation": (
            "Copied only the selected UBT response files, removed the already-"
            "proven stale Clang-19 -resource-dir token, and raised -ferror-limit "
            "for diagnostic visibility. Compiler argv[0] and all other UBT flags "
            "were preserved."
        ),
        "rewrites": rewrite_metadata,
        "results": results,
    }

    summary_path.write_text(
        json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    print()
    print(f"Summary: {summary_path}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
