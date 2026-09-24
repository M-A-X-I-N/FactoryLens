#!/usr/bin/env python3
from __future__ import annotations

import ast
from pathlib import Path
import subprocess


REQUIRED_PATHS = (
    Path("README.md"),
    Path("AGENTS.md"),
    Path("TASKS.md"),
    Path(".agents/README.md"),
    Path(".agents/WORKFLOW.md"),
    Path("docs/README.md"),
    Path("docs/PRODUCT_SCOPE.md"),
    Path("docs/MVP_CONTRACT.md"),
    Path("docs/IMPLEMENTATION_BOUNDARY.md"),
    Path("docs/ANALYZER_MODEL.md"),
    Path("docs/ENVIRONMENT.md"),
    Path("docs/ARCHITECTURE.md"),
    Path("docs/BUILDING.md"),
    Path("settings.gradle.kts"),
    Path("build.gradle.kts"),
    Path("gradle.properties"),
    Path("gradlew"),
    Path("gradlew.bat"),
    Path("gradle/wrapper/gradle-wrapper.jar"),
    Path("gradle/wrapper/gradle-wrapper.properties"),
    Path("core/build.gradle.kts"),
    Path("semantic-clangd/build.gradle.kts"),
    Path("cli/build.gradle.kts"),
    Path("src/main/resources/META-INF/plugin.xml"),
    Path("research/README.md"),
    Path("research/external-call-map-feasibility/README.md"),
    Path("research/satisfactory-entry-points/README.md"),
    Path(".gitignore"),
    Path(".env.example"),
)

FORBIDDEN_TASK_LEDGERS = (
    Path("TODO.md"),
    Path("docs/TODO.md"),
    Path("docs/TASKS.md"),
)


def repository_root() -> Path:
    return Path(__file__).resolve().parents[1]


def tracked_paths(root: Path) -> list[Path]:
    completed = subprocess.run(
        ["git", "ls-files", "-z"],
        cwd=root,
        capture_output=True,
        check=True,
    )
    return [
        Path(item.decode("utf-8"))
        for item in completed.stdout.split(b"\0")
        if item
    ]


def collect_errors(root: Path) -> list[str]:
    errors: list[str] = []

    for path in REQUIRED_PATHS:
        if not (root / path).is_file():
            errors.append(f"missing required file: {path.as_posix()}")

    for path in FORBIDDEN_TASK_LEDGERS:
        if (root / path).exists():
            errors.append(
                f"repository-local task ledger is not allowed: {path.as_posix()}"
            )

    try:
        tracked = tracked_paths(root)
    except (OSError, subprocess.CalledProcessError) as exc:
        errors.append(f"could not inspect tracked files: {exc}")
        tracked = []

    for path in tracked:
        posix = path.as_posix()
        if posix == ".env":
            errors.append(".env must remain untracked")
        if posix == "work" or posix.startswith("work/"):
            errors.append(f"generated work state must remain untracked: {posix}")

        if path.suffix == ".py":
            full = root / path
            try:
                ast.parse(full.read_text(encoding="utf-8"), filename=str(path))
            except (OSError, UnicodeError, SyntaxError) as exc:
                errors.append(
                    f"Python syntax/read failure in {path.as_posix()}: {exc}"
                )

    return errors


def main() -> int:
    root = repository_root()
    errors = collect_errors(root)
    if errors:
        print("FactoryLens repository consistency check failed:")
        for error in errors:
            print(f"  - {error}")
        return 1

    print("FactoryLens repository consistency check passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
