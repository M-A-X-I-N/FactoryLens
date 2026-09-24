#!/usr/bin/env python3
from __future__ import annotations

import argparse
from datetime import datetime
import json
import os
from pathlib import Path
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
        raise RuntimeError("B1 engine discovery currently targets the configured Windows workspace.")

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


def read_compilation_database(path: Path) -> list[dict[str, Any]]:
    parsed = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(parsed, list):
        raise RuntimeError(f"Compilation database is not a JSON array: {path}")
    return parsed


def normalized(path: str | Path) -> str:
    return os.path.normcase(os.path.abspath(os.fspath(path))).replace("\\", "/")


def command_text(entry: dict[str, Any]) -> str | None:
    command = entry.get("command")
    if isinstance(command, str):
        return command
    arguments = entry.get("arguments")
    if isinstance(arguments, list):
        return " ".join(str(item) for item in arguments)
    return None


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Generate or summarize the B1 UBT compilation database."
    )
    parser.add_argument(
        "--summarize-existing",
        action="store_true",
        help="Reuse the existing compile_commands.json instead of invoking UBT.",
    )
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

    out_dir = repo / "work" / "external-call-map" / "b1"
    log_path = out_dir / "ubt-generate-clang-database.log"
    database_path = out_dir / "compile_commands.json"
    summary_path = out_dir / "b1-summary.json"
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"Repository : {repo}")
    print(f"SML project: {sml_root}")
    print(f"Engine     : {engine_root}")
    print(f"UBT frontend: {build_bat}")
    print(f"Output     : {out_dir}")
    print()

    if not args.summarize_existing:
        print("Generating compile metadata with FactoryEditor / Win64 / Development...")
        completed = run_batch(
            build_bat,
            [
                "FactoryEditor",
                "Win64",
                "Development",
                f"-Project={project}",
                "-Compiler=VisualStudio2022",
                "-Mode=GenerateClangDatabase",
                f"-OutputDir={out_dir}",
                "-WaitMutex",
            ],
        )
        log_text = (completed.stdout or "") + (completed.stderr or "")
        log_path.write_text(log_text, encoding="utf-8")
        if log_text:
            print(log_text, end="" if log_text.endswith("\n") else "\n")
        if completed.returncode != 0:
            raise RuntimeError(
                f"UBT exited with code {completed.returncode}. Preserve the log at: {log_path}"
            )
    else:
        print("Skipping UBT generation and summarizing the existing compile database.")

    if not database_path.is_file():
        raise RuntimeError(f"No compile_commands.json was found at: {database_path}")

    entries = read_compilation_database(database_path)
    rss_entries = [
        entry
        for entry in entries
        if "/mods/gamefeatures/rss/source/" in normalized(entry.get("file", "")).lower()
        or "/vendor/rss-current/rss/source/" in normalized(entry.get("file", "")).lower()
    ]
    wiremod_entries = [
        entry
        for entry in entries
        if "/mods/ficsitwiremod/source/" in normalized(entry.get("file", "")).lower()
        or "/vendor/circuitry-current/source/" in normalized(entry.get("file", "")).lower()
    ]

    def sample(items: list[dict[str, Any]]) -> dict[str, Any] | None:
        if not items:
            return None
        entry = items[0]
        return {
            "file": entry.get("file"),
            "directory": entry.get("directory"),
            "command": command_text(entry),
        }

    summary = {
        "generated_at": datetime.now().astimezone().isoformat(),
        "repository": str(repo),
        "sml_project": str(sml_root),
        "engine": str(engine_root),
        "target": "FactoryEditor",
        "platform": "Win64",
        "configuration": "Development",
        "requested_compiler": "VisualStudio2022",
        "database": str(database_path),
        "database_bytes": database_path.stat().st_size,
        "total_entries": len(entries),
        "rss2_entries": len(rss_entries),
        "wiremod_entries": len(wiremod_entries),
        "rss2_sample": sample(rss_entries),
        "wiremod_sample": sample(wiremod_entries),
    }

    summary_json = json.dumps(summary, indent=2, ensure_ascii=False)
    summary_path.write_text(summary_json + "\n", encoding="utf-8")
    print(summary_json)
    print()
    print(f"B1 summary written to: {summary_path}")
    print(f"UBT log written to:    {log_path}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
