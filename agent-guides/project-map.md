# 项目地图

## 技术栈

- Java 17、Maven 3.9、Spring Boot 4、Spring Security、MyBatis、MySQL
- Spring Cloud Gateway、Keycloak、Redis、OAuth2/OIDC
- Vue 3、Vue Router、Vite、TypeScript、Tiptap、Lucide
- OpenAPI 3.1、Python 3.11+、uv、httpx
- JUnit 5、Mockito、MockMvc、Node Test Runner

## 仓库结构

```text
backend/                                  Backend、Maven、数据库脚本和容器配置
backend/src/main/java/com/mike/workstation/  后端业务代码
backend/src/test/                         后端测试

gateway/                                  Gateway/BFF、认证配置和测试

frontend/                                 Vue 应用、浏览器 API 适配和 Web 容器

openapi/                                  HTTP API 契约和生成器配置

clients/python/                           OpenAPI 生成的 Python Client
clients/python-auth/                      Python Client Credentials 认证薄层
clients/typescript/                       独立 TypeScript/Node SDK

keycloak/                                 Realm 首次导入模板

.github/workflows/                        CI、SDK 发布和应用发布流程

docs/                                     面向人的流程与进度文档

nginx/                                    可选的本机 Nginx 配置
```
