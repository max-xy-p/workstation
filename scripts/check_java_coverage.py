"""检查 Backend 与 Gateway 的 JaCoCo 综合覆盖率是否达到 80%。"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
THRESHOLD = 80.0
MODULES = ("backend", "gateway")


def read_counts(path: Path) -> tuple[int, int]:
    root = ET.parse(path).getroot()
    counters = {counter.attrib["type"]: counter for counter in root.findall("counter")}
    covered = 0
    total = 0
    for kind in ("LINE", "BRANCH"):
        counter = counters[kind]
        current_covered = int(counter.attrib["covered"])
        covered += current_covered
        total += current_covered + int(counter.attrib["missed"])
    if total == 0:
        raise ValueError(f"{path} 没有可统计的行或分支")
    return covered, total


def meets_threshold(covered: int, total: int) -> bool:
    return covered * 100 >= total * THRESHOLD


def main() -> int:
    covered = 0
    total = 0
    try:
        for module in MODULES:
            path = ROOT / module / "target/site/jacoco/jacoco.xml"
            module_covered, module_total = read_counts(path)
            covered += module_covered
            total += module_total
            print(f"{module}: {module_covered / module_total:.2%}")
    except (OSError, ET.ParseError, KeyError, ValueError) as error:
        print(f"无法检查 Java 覆盖率：{error}", file=sys.stderr)
        return 1

    percent = covered / total * 100
    print(f"Backend + Gateway 综合覆盖率：{percent:.2f}%（要求至少 {THRESHOLD:.0f}%）")
    if not meets_threshold(covered, total):
        print("Java 综合覆盖率未达到要求", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
