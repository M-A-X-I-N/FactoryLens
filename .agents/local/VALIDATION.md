# FactoryLens validation and human-run tooling

Read this file when creating repository validation tooling or when validation must be run manually by the human.

Prefer CI or other automated validation whenever it can prove the changed surface. Human-run validation is a close-to-last-resort path for environment-specific evidence.

When human execution is genuinely required:

- prefer a committed script under `scripts/` instead of pasting a multi-command procedure into chat;
- task-specific validation scripts include the task ID in the filename, for example `FL-B100-validate-compile-metadata.ps1`;
- generic validation/tooling scripts that are not tied to one roadmap task do not need a task ID;
- a genuinely single-line command may be given directly without creating a script;
- a platform-specific language is acceptable when it is the practical way to exercise a platform-specific environment, such as PowerShell for Windows-only validation;
- keep the script narrow, reproducible, and suitable for rerunning after a fresh checkout when practical.
