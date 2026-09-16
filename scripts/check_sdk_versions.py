"""校验 OpenAPI、生成器配置、SDK 包和发布标签的版本是否一致。"""

from __future__ import annotations

import argparse
import json
import re
import sys
import tomllib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SEMVER_PATTERN = re.compile(
    r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)"
    r"(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?"
    r"(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$"
)


def read_toml(path: Path) -> dict:
    with path.open("rb") as file:
        return tomllib.load(file)


def read_openapi_version(path: Path) -> str:
    content = path.read_text(encoding="utf-8")
    match = re.search(r"(?m)^info:\s*$\n^  title:.*$\n^  version:\s*([^\s#]+)", content)
    if not match:
        raise ValueError(f"无法从 {path} 读取 info.version")
    return match.group(1)


def read_generator_version(path: Path) -> str:
    content = path.read_text(encoding="utf-8")
    match = re.search(r"(?m)^package_version_override:\s*([^\s#]+)", content)
    if not match:
        raise ValueError(f"无法从 {path} 读取 package_version_override")
    return match.group(1)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--tag",
        help="可选的 SDK 发布标签，格式必须为 sdk-v<OpenAPI 版本>",
    )
    args = parser.parse_args()

    openapi_version = read_openapi_version(ROOT / "openapi/workstation.yaml")
    python_generator_version = read_generator_version(
        ROOT / "openapi/python-client-config.yaml"
    )
    python_project = read_toml(ROOT / "clients/python/pyproject.toml")["project"]
    auth_project = read_toml(ROOT / "clients/python-auth/pyproject.toml")["project"]
    typescript_project = json.loads(
        (ROOT / "clients/typescript/package.json").read_text(encoding="utf-8")
    )

    versions = {
        "OpenAPI info.version": openapi_version,
        "Python 生成器配置": python_generator_version,
        "Python SDK": python_project["version"],
        "Python 认证层": auth_project["version"],
        "TypeScript SDK": typescript_project["version"],
    }

    errors: list[str] = []
    if not SEMVER_PATTERN.fullmatch(openapi_version):
        errors.append(f"OpenAPI 版本不是有效的语义化版本：{openapi_version}")

    for name, version in versions.items():
        if version != openapi_version:
            errors.append(
                f"{name} 的版本为 {version}，应与 OpenAPI 版本 {openapi_version} 一致"
            )

    expected_dependency = f"workstation-api-client=={openapi_version}"
    if expected_dependency not in auth_project.get("dependencies", []):
        errors.append(
            "Python 认证层必须精确依赖 "
            f"{expected_dependency}，当前依赖为 {auth_project.get('dependencies', [])}"
        )

    if args.tag:
        expected_tag = f"sdk-v{openapi_version}"
        if args.tag != expected_tag:
            errors.append(f"发布标签为 {args.tag}，应为 {expected_tag}")

    if errors:
        for error in errors:
            print(f"错误：{error}", file=sys.stderr)
        return 1

    print(f"SDK 版本一致：{openapi_version}")
    if args.tag:
        print(f"SDK 发布标签有效：{args.tag}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
