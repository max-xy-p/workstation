# Nginx 本地代理配置

前端 API 地址使用同源路径 `/api`，由 nginx 转发到 Spring Boot。浏览器只访问 nginx，因此不需要后端配置 CORS。

## 开发模式

启动后端：

```powershell
mvn "-Dmaven.repo.local=target/maven-repo" spring-boot:run
```

启动前端：

```powershell
cd frontend
npm.cmd run dev -- --host 127.0.0.1
```

把 `nginx/workstation.dev.conf` 作为 server block 放进 nginx 的 `http {}` 中。例如在 nginx 主配置里加入：

```nginx
include D:/dev/project/workstation/nginx/workstation.dev.conf;
```

然后访问：

```text
http://localhost:8088
```

如果页面显示 `Unexpected token '<', "<!doctype "... is not valid JSON`，说明 `/api/...` 请求拿到的是前端 HTML 页面，不是后端 JSON。常见原因是直接访问了 Vite 地址 `http://127.0.0.1:5173`，或者 nginx 没有加载本配置；请改用 `http://localhost:8088` 并确认 `/api/` location 已生效。

## 构建产物模式

构建前端：

```powershell
cd frontend
npm.cmd run build
```

把 `nginx/workstation.prod.conf` 作为 server block 放进 nginx 的 `http {}` 中。例如：

```nginx
include D:/dev/project/workstation/nginx/workstation.prod.conf;
```

然后访问：

```text
http://localhost:8088
```

如果项目路径不是 `D:/dev/project/workstation`，需要同步修改 `workstation.prod.conf` 中的 `root`。
