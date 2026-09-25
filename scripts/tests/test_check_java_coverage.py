"""覆盖率检查脚本的关键口径测试。"""

import tempfile
import unittest
from pathlib import Path

from scripts.check_java_coverage import meets_threshold, read_counts


class CheckJavaCoverageTests(unittest.TestCase):
    def test_accepts_exact_threshold_and_rejects_lower_coverage(self):
        self.assertTrue(meets_threshold(80, 100))
        self.assertFalse(meets_threshold(79, 100))

    def test_weights_lines_and_branches_by_their_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "jacoco.xml"
            path.write_text(
                '<report name="example">'
                '<counter type="LINE" missed="20" covered="80"/>'
                '<counter type="BRANCH" missed="8" covered="2"/>'
                '</report>',
                encoding="utf-8",
            )

            self.assertEqual((82, 110), read_counts(path))

    def test_rejects_report_without_branch_counter(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "jacoco.xml"
            path.write_text(
                '<report name="example"><counter type="LINE" missed="0" covered="1"/></report>',
                encoding="utf-8",
            )

            with self.assertRaises(KeyError):
                read_counts(path)


if __name__ == "__main__":
    unittest.main()
