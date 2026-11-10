#!/usr/bin/env python3
"""Exercise the mobile wording guard with injected Kotlin UI strings."""

from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CHECKER = ROOT / "scripts" / "check-mobile-ui-vocabulary.py"


class MobileUiVocabularyTests(unittest.TestCase):
    def run_checker(
        self,
        messages: list[str],
        suffix: str = ".kt",
        documentation: list[str] | None = None,
    ) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as temporary_directory:
            source_root = Path(temporary_directory) / "source"
            source_root.mkdir()
            declarations = "\n".join(
                f'{"val" if suffix == ".kt" else "let"} message{index} = "{message}"'
                for index, message in enumerate(messages)
            )
            (source_root / f"InjectedStrings{suffix}").write_text(
                f"{ 'package test' if suffix == '.kt' else '' }\n{declarations}\n",
                encoding="utf-8",
            )
            documentation_root = Path(temporary_directory) / "docs"
            documentation_root.mkdir()
            for index, content in enumerate(documentation or ["Public docs are clean."]):
                (documentation_root / f"doc-{index}.md").write_text(
                    content, encoding="utf-8"
                )
            return subprocess.run(
                [
                    sys.executable,
                    str(CHECKER),
                    "--source-root",
                    str(source_root),
                    "--documentation-root",
                    str(documentation_root),
                ],
                cwd=ROOT,
                check=False,
                capture_output=True,
                text=True,
            )

    def test_checker_rejects_each_new_writing_violation(self) -> None:
        violations = (
            ("The server returned an unreadable transaction. Nothing was changed.", "next step"),
            ("Kept it on this device and discarded your edit.", "subject"),
            ("Cleared the pending deletion here.", "subject"),
            ("Actual controls this split child field through its parent.", "internal terms"),
            ("Compare this revision with Actual.", "internal terms"),
            ("The operation failed.", "internal terms"),
            ("The projection is out of date.", "internal terms"),
            ("Use a comma for cents in this budget.", "decimals"),
            ("The server didn’t confirm this conflict.", "conflict"),
            ("This edit is uncertain.", "conflict"),
            ("Needs confirmation", "Needs review"),
            ("Still unconfirmed", "Needs review"),
            ("Change not applied", "Needs review"),
            ("Not applied", "Needs review"),
            ("Keep last synced", "what is kept"),
        )
        for message, expected_rule in violations:
            with self.subTest(message=message):
                result = self.run_checker([message])
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertIn(expected_rule, result.stderr)

    def test_checker_accepts_rewritten_messages(self) -> None:
        messages = [
            "Tandem couldn’t read the latest transaction, so your edit stays on this device. Sync the budget and try again.",
            "Tandem kept Actual’s latest version and removed your edit.",
            "Tandem removed the waiting deletion after the server confirmed the transaction was deleted.",
            "Use a comma for decimals in this budget, such as 12,00.",
            "Status unknown",
            "Needs review",
            "Rejected",
            "Keep Actual’s version",
        ]
        result = self.run_checker(messages)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_checker_scans_native_swift_ui_strings(self) -> None:
        result = self.run_checker(["This edit is uncertain."], suffix=".swift")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("avoid ‘conflict’ or ‘uncertain’", result.stderr)

    def test_checker_rejects_old_product_name_in_public_docs(self) -> None:
        product_name = "mobile " + "com" + "panion"
        result = self.run_checker([], documentation=[f"The {product_name} is ready."])
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("old product name", result.stderr)

    def test_checker_rejects_internal_ticket_identifiers_in_public_docs(self) -> None:
        identifier = "ABC" + "-" + "42"
        result = self.run_checker([], documentation=[f"See {identifier} for the change."])
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("internal ticket identifiers", result.stderr)

    def test_checker_accepts_public_product_wording(self) -> None:
        result = self.run_checker(
            [], documentation=["Tandem is a local-first app for Actual Budget."]
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
