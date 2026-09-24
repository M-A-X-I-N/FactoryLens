#!/usr/bin/env python3
from __future__ import annotations

import argparse
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


def response_file_from_command(command: str) -> Path:
    match = RSP_TOKEN.search(command)
    if not match:
        raise RuntimeError(
            f"Compilation command does not contain a response file: {command}"
        )
    return Path(match.group(1) or match.group(2))


def strip_resource_dir(text: str) -> tuple[str, list[str]]:
    matches = [match.group(0) for match in RESOURCE_DIR_TOKEN.finditer(text)]
    stripped = RESOURCE_DIR_TOKEN.sub("", text)
    return stripped, matches


def rewrite_entry(
    *,
    entry: dict[str, Any],
    rsp_dir: Path,
) -> tuple[dict[str, Any], dict[str, Any]]:
    command = entry.get("command")
    if not isinstance(command, str):
        raise RuntimeError(
            "B3 resource-dir alignment currently expects compile database "
            "entries with a command string."
        )

    original_rsp = response_file_from_command(command)
    if not original_rsp.is_file():
        raise RuntimeError(f"Response file not found: {original_rsp}")

    original_text = original_rsp.read_text(
        encoding="utf-8-sig", errors="replace"
    )
    rewritten_text, removed = strip_resource_dir(original_text)

    if not removed:
        raise RuntimeError(
            f"No explicit -resource-dir token found in response file: {original_rsp}"
        )

    rsp_dir.mkdir(parents=True, exist_ok=True)
    rewritten_rsp = rsp_dir / original_rsp.name
    rewritten_rsp.write_text(rewritten_text, encoding="utf-8")

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

    return rewritten_entry, {
        "file": entry.get("file"),
        "original_response_file": str(original_rsp),
        "rewritten_response_file": str(rewritten_rsp),
        "removed_resource_dir_tokens": removed,
        "compiler_unchanged": command.split("@", 1)[0].strip(),
    }


def source_diagnostics(lines: list[str]) -> list[str]:
    return [
        line
        for line in lines
        if line.startswith("E[")
        and "] [" in line
        and " Line " in line
        and ":" in line
    ]


def effective_resource_dirs(lines: list[str]) -> list[str]:
    values: list[str] = []
    pattern = re.compile(r'-resource-dir=(?:"([^"]+)"|(\S+))')
    for line in lines:
        for match in pattern.finditer(line):
            value = match.group(1) or match.group(2)
            if value not in values:
                values.append(value)
    return values


def run_check(
    *,
    clangd: Path,
    database_dir: Path,
    source: Path,
    output_dir: Path,
) -> dict[str, Any]:
    log_path = output_dir / f"{source.stem}-aligned-resource-dir-check.log"
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

    diagnostics = source_diagnostics(lines)
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

    result = {
        "file": str(source),
        "exit_code": completed.returncode,
        "reported_check_errors": reported_errors,
        "source_diagnostic_count": len(diagnostics),
        "source_diagnostics": diagnostics[:50],
        "builtin_prefetch_diagnostics": builtin_prefetch,
        "effective_resource_dirs": effective_resource_dirs(lines),
        "log": str(log_path),
    }

    print()
    print(f"==> {source.name}")
    print(f"    exit code:             {completed.returncode}")
    print(f"    reported errors:       {reported_errors}")
    print(f"    source diagnostics:    {len(diagnostics)}")
    print(f"    _m_prefetch failures:  {len(builtin_prefetch)}")
    print(
        "    effective resource dir: "
        + (", ".join(result["effective_resource_dirs"]) or "<not parsed>")
    )
    for diagnostic in diagnostics[:10]:
        print(f"      {diagnostic}")

    return result


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "B3 A/B probe: remove only the stale Clang-19 resource-dir "
            "from copied UBT response files and rerun clangd 22 checks."
        )
    )
    parser.add_argument(
        "--clangd",
        help="Explicit path to clangd.exe; standalone LLVM is preferred.",
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
        / "resource-dir-alignment"
    )
    database_dir = output_dir / "compile-db"
    rsp_dir = database_dir / "response-files"
    summary_path = output_dir / "b3-resource-dir-alignment-summary.json"
    database_dir.mkdir(parents=True, exist_ok=True)

    rewritten_entries: list[dict[str, Any]] = []
    rewrites: list[dict[str, Any]] = []
    for _, entry in selected:
        rewritten, metadata = rewrite_entry(entry=entry, rsp_dir=rsp_dir)
        rewritten_entries.append(rewritten)
        rewrites.append(metadata)

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

    prefetch_cleared = all(
        len(result["builtin_prefetch_diagnostics"]) == 0
        for result in results
    )

    summary = {
        "generated_at": datetime.now().astimezone().isoformat(),
        "status": "resource-dir-alignment-completed",
        "clangd": str(clangd),
        "clangd_version": clangd_version,
        "source_database": str(source_database),
        "derived_database": str(derived_database),
        "transformation": (
            "Copied only the selected UBT response files and removed their "
            "explicit -resource-dir tokens. Compiler argv[0] and all other "
            "response-file flags were preserved."
        ),
        "rewrites": rewrites,
        "results": results,
        "builtin_prefetch_failure_cleared": prefetch_cleared,
    }

    summary_path.write_text(
        json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    print()
    print(f"_m_prefetch failure cleared: {prefetch_cleared}")
    print(f"Summary: {summary_path}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
