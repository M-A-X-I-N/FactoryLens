from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
CHECKER_PATH = ROOT / "scripts" / "check_repository_consistency.py"

spec = importlib.util.spec_from_file_location(
    "factorylens_repository_consistency",
    CHECKER_PATH,
)
assert spec is not None and spec.loader is not None
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class RepositoryConsistencyTests(unittest.TestCase):
    def test_current_repository_is_consistent(self) -> None:
        self.assertEqual([], checker.collect_errors(ROOT))


if __name__ == "__main__":
    unittest.main()
