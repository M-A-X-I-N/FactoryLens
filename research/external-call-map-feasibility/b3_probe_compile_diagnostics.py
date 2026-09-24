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


def command_text(entry: dict[str, Any]) -> str:
    command = entry.get("command")
    if isinstance(command, str):
        return command
    arguments = entry.get("arguments")
    if isinstance(arguments, list):
        return " ".join(str(item) for item in arguments)
    return ""


def command_metadata(command: str) -> dict[str, Any]:
    compiler_match = re.match(r'^"([^"]+)"|^(\S+)', command)
    compiler = None
    if compiler_match:
        compiler = compiler_match.group(1) or compiler_match.group(2)

    resource_dir = None
    resource_match = re.search(
        r'(?:"-resource-dir=([^"]+)"|-resource-dir=(\S+))',
        command,
    )
    if resource_match:
        resource_dir = resource_match.group(1) or resource_match.group(2)

    return {
        "compiler": compiler,
        "resource_dir": resource_dir,
        "has_werror": "-Werror" in command,
        "ms_compatibility_version": (
            re.search(r"-fms-compatibility-version=([^\s]+)", command).group(1)
            if re.search(r"-fms-compatibility-version=([^\s]+)", command)
            else None
        ),
    }


def run_check(
    *,
    clangd: Path,
    database_dir: Path,
    source: Path,
    output_dir: Path,
) -> dict[str, Any]:
    label = source.stem
    log_path = output_dir / f"{label}-clangd22-check.log"

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

    diagnostics = [
        line
        for line in lines
        if line.startswith("E[")
        and "] [" in line
        and " Line " in line
        and ":" in line
    ]
    tweak_failures = [
        line
        for line in lines
        if line.startswith("E[")
        and "tweak:" in line
        and "==> FAIL:" in line
    ]
    other_errors = [
        line
        for line in lines
        if (
            line.startswith("E[")
            or " error:" in line.lower()
            or "fatal error:" in line.lower()
        )
        and line not in diagnostics
        and line not in tweak_failures
    ]

    reported_errors: int | None = None
    for line in lines:
        match = re.search(r"All checks completed,\s*(\d+)\s+errors?", line)
        if match:
            reported_errors = int(match.group(1))

    print()
    print(f"==> {source.name}")
    print(f"    exit code:          {completed.returncode}")
    print(f"    reported errors:    {reported_errors}")
    print(f"    source diagnostics: {len(diagnostics)}")
    for diagnostic in diagnostics[:10]:
        print(f"      {diagnostic}")
    print(f"    tweak failures:     {len(tweak_failures)}")
    print(f"    other errors:       {len(other_errors)}")
    print(f"    log:                {log_path}")

    return {
        "file": str(source),
        "exit_code": completed.returncode,
        "reported_check_errors": reported_errors,
        "source_diagnostic_count": len(diagnostics),
        "source_diagnostics": diagnostics[:50],
        "tweak_failure_count": len(tweak_failures),
        "tweak_failure_sample": tweak_failures[:20],
        "other_error_count": len(other_errors),
        "other_error_sample": other_errors[:30],
        "log": str(log_path),
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "B3 diagnostic probe: expose concrete clangd --check diagnostics "
            "for RSS2 TUs that background indexing marked incomplete."
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

    database_dir = (
        repo
        / "work"
        / "external-call-map"
        / "b2"
        / "clang-database"
    )
    database_path = database_dir / "compile_commands.json"
    if not database_path.is_file():
        raise RuntimeError(
            f"B2 Clang-derived compile database not found: {database_path}"
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

    for source in sources:
        if not source.is_file():
            raise RuntimeError(f"Representative RSS2 file not found: {source}")

    entries = read_database(database_path)
    selected_entries: list[dict[str, Any]] = []
    for source in sources:
        entry = find_entry(entries, source)
        if entry is None:
            raise RuntimeError(
                f"Representative RSS2 file missing from compile database: {source}"
            )
        selected_entries.append(entry)

    output_dir = (
        repo
        / "work"
        / "external-call-map"
        / "b3"
        / "compile-diagnostics"
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    summary_path = output_dir / "b3-compile-diagnostics-summary.json"

    print(f"Repository:      {repo}")
    print(f"SML project:     {sml_root}")
    print(f"clangd:          {clangd}")
    print(f"Compile DB:      {database_path}")
    print()
    print(clangd_version)

    results = [
        run_check(
            clangd=clangd,
            database_dir=database_dir,
            source=source,
            output_dir=output_dir,
        )
        for source in sources
    ]

    summary = {
        "generated_at": datetime.now().astimezone().isoformat(),
        "status": "compile-diagnostics-completed",
        "clangd": str(clangd),
        "clangd_version": clangd_version,
        "database": str(database_path),
        "database_entries": len(entries),
        "command_metadata": [
            {
                "file": entry.get("file"),
                **command_metadata(command_text(entry)),
            }
            for entry in selected_entries
        ],
        "results": results,
    }

    summary_json = json.dumps(summary, indent=2, ensure_ascii=False)
    summary_path.write_text(summary_json + "\n", encoding="utf-8")
    print()
    print(f"Summary: {summary_path}")

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
