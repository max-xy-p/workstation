"""核对生产挂载文件均由发布流程上传，且缺文件时拒绝启动。"""
import fnmatch
import json
from pathlib import Path, PurePosixPath
import shlex
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[2]


class DeploymentConfigurationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        result = subprocess.run(
            ["docker", "compose", "--env-file", ".env.docker.example",
             "-f", "compose.prod.yaml", "config", "--format", "json"],
            cwd=ROOT, check=True, capture_output=True, text=True,
        )
        config = json.loads(result.stdout)
        cls.mounts = [
            mount for service in config["services"].values()
            for mount in service.get("volumes", [])
            if mount["type"] == "bind" and mount.get("read_only")
        ]

    def test_every_required_file_is_uploaded_to_its_mounted_path(self):
        workflow = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
        uploads = []
        for line in workflow.replace("\\\n", " ").splitlines():
            if line.strip().startswith("scp "):
                args = shlex.split(line)
                uploads.extend((source, args[-1]) for source in args[3:-1])
        self.assertTrue(self.mounts)
        for mount in self.mounts:
            source = Path(mount["source"])
            relative = source.relative_to(ROOT).as_posix()
            with self.subTest(source=relative):
                self.assertTrue(source.is_file(), "生产挂载源必须是仓库中的文件")
                destinations = []
                for pattern, destination in uploads:
                    if fnmatch.fnmatchcase(relative, pattern):
                        remote = destination.split("$DEPLOY_PATH/", 1)[-1]
                        if remote.endswith("/"):
                            remote += PurePosixPath(relative).name
                        destinations.append(remote)
                self.assertIn(relative, destinations, "发布遗漏文件或上传路径与挂载不一致")

    def test_missing_file_mounts_do_not_create_directories(self):
        self.assertTrue(self.mounts)
        for mount in self.mounts:
            with self.subTest(source=mount["source"]):
                self.assertIs(mount.get("bind", {}).get("create_host_path"), False)
