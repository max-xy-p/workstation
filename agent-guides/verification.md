# 常用验证命令

## Backend

```powershell
cd backend

mvn.cmd test
mvn.cmd verify
mvn.cmd package
```

## Gateway

```powershell
cd gateway

mvn.cmd test
mvn.cmd verify
mvn.cmd package
```

## TypeScript SDK

```powershell
cd clients/typescript

npm.cmd ci
npm.cmd run api:generate
npm.cmd run typecheck
npm.cmd run build
npm.cmd test
```

## Frontend

```powershell
cd frontend

npm.cmd ci
npm.cmd run typecheck
npm.cmd run build
```

Frontend 当前没有 `test`、`lint` 或 `format` 命令，不得声称运行过这些检查。

## Python Client Credentials 认证层

```powershell
uv sync --project clients/python-auth --locked

uv run --project clients/python-auth python -m unittest discover -s clients/python-auth/tests -v
```

## SDK 版本一致性

```powershell
python scripts/check_sdk_versions.py
```

## SonarQube

Agent 优先通过 SonarQube MCP 获取规则和项目分析结果。

CLI 主要用于人工检查、认证诊断和 MCP 故障排查。

云端 Issue 列表只反映最近一次成功分析纳入的文件。检查 Java 问题时，先确认 CI 扫描已经使用两个 Maven 模块的编译产物，并确认云端文件索引包含 Backend 和 Gateway；本地未提交修改不会出现在云端列表中。仓库级扫描范围和 Quality Gate 等待行为由 `sonar-project.properties` 与 `.github/workflows/ci.yml` 维护。

```powershell
sonar auth status
sonar list projects
```

已知 Project Key 时，可以人工查询项目问题：

```powershell
sonar list issues --project <SONAR_PROJECT_KEY> --format table
```

不要把 Token 直接写在命令、脚本、`.codex/config.toml` 或本文件中。
