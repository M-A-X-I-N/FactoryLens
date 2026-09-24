#!/usr/bin/env python3
from __future__ import annotations

import argparse
from datetime import datetime
import json
import os
from pathlib import Path
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


def registered_engine_root() -> Path:
    if os.name != "nt":
        raise RuntimeError("B2 compiler-view comparison currently targets the configured Windows workspace.")

    import winreg

    with winreg.OpenKey(
        winreg.HKEY_CURRENT_USER,
        r"SOFTWARE\Epic Games\Unreal Engine\Builds",
    ) as key:
        try:
            value, _ = winreg.QueryValueEx(key, "5.6.1-CSS")
        except FileNotFoundError as exc:
            raise RuntimeError(
                r"Registered engine 5.6.1-CSS was not found under "
                r"HKCU:\SOFTWARE\Epic Games\Unreal Engine\Builds"
            ) from exc

    return Path(value).resolve()


def run_batch(batch_file: Path, args: list[str]) -> subprocess.CompletedProcess[str]:
    command_text = subprocess.list2cmdline([str(batch_file), *args])
    return subprocess.run(
        [os.environ.get("COMSPEC", "cmd.exe"), "/d", "/s", "/c", command_text],
        cwd=batch_file.parent,
        capture_output=True,
        text=True,
        errors="replace",
        check=False,
    )


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


def read_compilation_database(path: Path) -> list[dict[str, Any]]:
    parsed = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(parsed, list):
        raise RuntimeError(f"Compilation database is not a JSON array: {path}")
    return parsed


def normalized(path: str | Path) -> str:
    return os.path.normcase(os.path.abspath(os.fspath(path)))


def find_entry(entries: list[dict[str, Any]], source: Path) -> dict[str, Any] | None:
    needle = normalized(source)
    for entry in entries:
        file_value = entry.get("file")
        if isinstance(file_value, str) and normalized(file_value) == needle:
            return entry
    return None


def check_clangd(
    *,
    label: str,
    clangd: Path,
    database_dir: Path,
    source: Path,
    output_dir: Path,
) -> dict[str, Any]:
    log_path = output_dir / f"{label}-clangd-check.log"
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

    reported_check_errors: int | None = None
    for line in lines:
        marker = "All checks completed,"
        if marker not in line:
            continue
        tail = line.split(marker, 1)[1].strip()
        first = tail.split()[0] if tail else ""
        if first.isdigit():
            reported_check_errors = int(first)

    source_diagnostics = [
        line
        for line in lines
        if line.startswith("E[") and "] [" in line and " Line " in line and ":" in line
    ]
    tweak_failures = [
        line
        for line in lines
        if line.startswith("E[") and "tweak:" in line and "==> FAIL:" in line
    ]
    other_errors = [
        line
        for line in lines
        if (
            line.startswith("E[")
            or "error:" in line.lower()
            or "fatal error:" in line.lower()
        )
        and line not in source_diagnostics
        and line not in tweak_failures
    ]

    print()
    print(f"==> clangd --check: {label}")
    print(f"    database:          {database_dir}")
    print(f"    file:              {source}")
    print(f"    exit code:         {completed.returncode}")
    print(f"    check errors:      {reported_check_errors}")
    print(f"    source diagnostics:{len(source_diagnostics):>4}")
    print(f"    tweak failures:    {len(tweak_failures):>4}")
    print(f"    other errors:      {len(other_errors):>4}")

    return {
        "label": label,
        "database": str(database_dir),
        "file": str(source),
        "exit_code": completed.returncode,
        "reported_check_errors": reported_check_errors,
        "source_diagnostic_count": len(source_diagnostics),
        "source_diagnostic_sample": source_diagnostics[:30],
        "tweak_failure_count": len(tweak_failures),
        "tweak_failure_sample": tweak_failures[:30],
        "other_error_count": len(other_errors),
        "other_error_sample": other_errors[:30],
        "log": str(log_path),
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Compare clangd against MSVC-derived and Clang-derived UBT databases."
    )
    parser.add_argument("--clangd", help="Explicit path to clangd.exe.")
    args = parser.parse_args()

    repo = repository_root()
    sml_value = os.environ.get("SML_PROJECT_ROOT") or read_dotenv_value(
        repo / ".env", "SML_PROJECT_ROOT"
    )
    if not sml_value:
        raise RuntimeError(
            "SML_PROJECT_ROOT is not set in the process environment or repository .env"
        )

    sml_root = resolve_configured_path(repo, sml_value)
    project = sml_root / "FactoryGame.uproject"
    if not project.is_file():
        raise RuntimeError(f"FactoryGame.uproject was not found at: {project}")

    engine_root = registered_engine_root()
    build_bat = engine_root / "Engine" / "Build" / "BatchFiles" / "Build.bat"
    if not build_bat.is_file():
        raise RuntimeError(f"Build.bat was not found at: {build_bat}")

    clangd, searched = resolve_clangd(args.clangd)
    if clangd is None:
        raise RuntimeError("clangd was not found. Checked: " + ", ".join(searched))

    b1_dir = repo / "work" / "external-call-map" / "b1"
    msvc_database_path = b1_dir / "compile_commands.json"
    if not msvc_database_path.is_file():
        raise RuntimeError(f"B1 MSVC compile database not found: {msvc_database_path}")

    b2_dir = repo / "work" / "external-call-map" / "b2"
    clang_database_dir = b2_dir / "clang-database"
    clang_database_path = clang_database_dir / "compile_commands.json"
    clang_database_log = b2_dir / "ubt-generate-clang-database.log"
    summary_path = b2_dir / "b2-comparison-summary.json"
    b2_dir.mkdir(parents=True, exist_ok=True)
    clang_database_dir.mkdir(parents=True, exist_ok=True)

    rss_file = (
        sml_root
        / "Mods"
        / "GameFeatures"
        / "RSS"
        / "Source"
        / "RSS"
        / "Private"
        / "Subsystem"
        / "RSSDataManagerSubsystem.cpp"
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
            raise RuntimeError(f"Representative B2 source file not found: {source}")

    clangd_version = subprocess.run(
        [str(clangd), "--version"],
        capture_output=True,
        text=True,
        errors="replace",
        check=False,
    ).stdout.strip()

    print(f"Repository:       {repo}")
    print(f"SML project:      {sml_root}")
    print(f"Engine:           {engine_root}")
    print(f"clangd:           {clangd}")
    print()
    print(clangd_version)

    msvc_entries = read_compilation_database(msvc_database_path)
    msvc_rss = find_entry(msvc_entries, rss_file)
    msvc_wiremod = find_entry(msvc_entries, wiremod_file)
    if not msvc_rss or not msvc_wiremod:
        raise RuntimeError(
            "One or both representative B2 files are missing from the B1 MSVC database."
        )

    print()
    print("Generating a second UBT compilation database with -Compiler=Clang...")
    generated = run_batch(
        build_bat,
        [
            "FactoryEditor",
            "Win64",
            "Development",
            f"-Project={project}",
            "-Compiler=Clang",
            "-Mode=GenerateClangDatabase",
            f"-OutputDir={clang_database_dir}",
            "-WaitMutex",
        ],
    )
    generation_output = (generated.stdout or "") + (generated.stderr or "")
    clang_database_log.write_text(generation_output, encoding="utf-8")

    clang_database_status = "generated"
    clang_entries: list[dict[str, Any]] = []
    clang_rss: dict[str, Any] | None = None
    clang_wiremod: dict[str, Any] | None = None

    if generated.returncode != 0:
        clang_database_status = "ubt-failed"
    elif not clang_database_path.is_file():
        clang_database_status = "database-missing"
    else:
        clang_entries = read_compilation_database(clang_database_path)
        clang_rss = find_entry(clang_entries, rss_file)
        clang_wiremod = find_entry(clang_entries, wiremod_file)
        if not clang_rss or not clang_wiremod:
            clang_database_status = "representative-files-missing"

    results = [
        check_clangd(
            label="msvc-rss2-data-manager",
            clangd=clangd,
            database_dir=b1_dir,
            source=rss_file,
            output_dir=b2_dir,
        ),
        check_clangd(
            label="msvc-wiremod-managed-sign",
            clangd=clangd,
            database_dir=b1_dir,
            source=wiremod_file,
            output_dir=b2_dir,
        ),
    ]

    if clang_database_status == "generated":
        results.extend(
            [
                check_clangd(
                    label="clang-rss2-data-manager",
                    clangd=clangd,
                    database_dir=clang_database_dir,
                    source=rss_file,
                    output_dir=b2_dir,
                ),
                check_clangd(
                    label="clang-wiremod-managed-sign",
                    clangd=clangd,
                    database_dir=clang_database_dir,
                    source=wiremod_file,
                    output_dir=b2_dir,
                ),
            ]
        )

    summary = {
        "generated_at": datetime.now().astimezone().isoformat(),
        "status": "comparison-completed",
        "clangd": str(clangd),
        "clangd_version": clangd_version,
        "target": "FactoryEditor",
        "platform": "Win64",
        "configuration": "Development",
        "msvc_database": {
            "path": str(msvc_database_path),
            "entries": len(msvc_entries),
            "rss2_command": msvc_rss.get("command"),
            "wiremod_command": msvc_wiremod.get("command"),
        },
        "clang_database": {
            "status": clang_database_status,
            "ubt_exit_code": generated.returncode,
            "path": str(clang_database_path),
            "log": str(clang_database_log),
            "entries": len(clang_entries),
            "rss2_command": clang_rss.get("command") if clang_rss else None,
            "wiremod_command": clang_wiremod.get("command") if clang_wiremod else None,
        },
        "results": results,
    }

    summary_json = json.dumps(summary, indent=2, ensure_ascii=False)
    summary_path.write_text(summary_json + "\n", encoding="utf-8")
    print()
    print(summary_json)
    print()
    print(f"B2 comparison summary written to: {summary_path}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
