# 公开注册与个人工作空间

Workstation 允许访客创建自己的独立账号。登录页的“创建新账号”会进入 Keycloak 原生注册页，用户填写资料、账号和密码，点击验证邮件中的链接后返回工作台。密码至少 12 个字符。Keycloak 管理验证链接与邮箱验证结果，邮件服务通过 Resend 统一投递；工作空间归属仍使用已验证令牌中的 sub，而不是邮箱或昵称。发送配置及故障边界见[邮件服务](mail.md)。

新账号自动获得 WORKSTATION_USER 普通系统准入角色，不获得 WORKSTATION_ADMIN、WORKSTATION_SERVICE 或身份管理权限。首次进入工作台时，Backend 沿用现有事务创建个人 Workspace，并赋予该账号此空间的 OWNER 权限。个人空间按主体唯一，重复进入不会重复创建；显示名称相同的账号仍是独立主体。

公开注册只授予使用自己的空间及自行创建共享空间的能力。用户不会自动加入其他人的共享空间，也不能读取或修改其他人的项目、笔记、任务；共享空间仍由其成员管理员添加成员。机器主体仍使用 Client Credentials，不自动获得个人空间。

## 账号与认证职责

账号、密码和注册校验由 Keycloak 维护。Frontend 只发起注册跳转，Gateway 在现有授权请求中添加标准 prompt=create，保留 state、nonce、PKCE 与原有回调校验；访问令牌仍保存在 Gateway，浏览器使用 Session 和写请求 CSRF。注册没有开放匿名业务接口，也没有增加另一套账号表或密码接口。标准入口见 [Keycloak 官方说明](https://www.keycloak.org/docs/26.3.5/server_admin/)。

WORKSTATION_INITIAL_USERNAME / PASSWORD 仅在首次导入 Realm 时创建初始平台账号，新用户不需要知道它们。该账号第一次登录仍需修改临时密码。KEYCLOAK_ADMIN_USERNAME / PASSWORD 用于身份中心管理控制台，是另一组管理员凭据；它们同样不是普通注册用户需要的信息。

## 新安装

仓库 Realm 模板已启用注册和邮箱验证，并配置只包含普通准入与账号自助管理权限的默认组合角色。开发与生产 Compose 继续使用同一份模板和包含邮件扩展的 Keycloak 镜像。注册验证完成后沿用既有个人空间初始化流程，不需要手动分配系统角色或手动创建个人空间。

## 已有 Realm 升级

Keycloak 的启动导入会跳过已有 Realm；修改模板或重启容器不会自动开放已有部署。先备份身份数据库，确认要修改的 Realm 和管理员身份，再使用 [注册配置工具](../scripts/configure_public_registration.py) 或管理控制台调整配置，不重新导入覆盖用户。

工具使用 Python 标准库，必填参数 --base-url 是包含实际 /auth 前缀的 Keycloak 地址，--realm 默认为 workstation。默认仅检查并显示变更计划；确认计划后加 --apply 才会提交。凭据可通过 KEYCLOAK_ADMIN_USERNAME、KEYCLOAK_ADMIN_PASSWORD 环境变量提供，未提供时交互输入，密码不回显；工具不自动读取或输出 .env.docker 的秘密。

工具将 WORKSTATION_USER 追加到当前默认组合角色，然后启用 registrationAllowed、保留既有 verifyEmail 策略，并将密码最低长度提升到至少 12。已有更强长度及其他密码策略保留；用户、客户端、已有角色关系和其他 Realm 设置保持。重复执行不会重复创建角色或用户。检测到默认角色含管理、机器或非账号自助客户端权限时，工具在写入前停止，要求管理员先核实默认授权。邮件验证另按[邮件升级流程](mail.md#新安装与已有部署)处理，不能在发送链路尚不可用时直接启用。

管理控制台的等价操作是开启 User registration、保留既有 Verify email 策略、配置至少 12 字符的密码长度，并在当前默认组合角色中加入 WORKSTATION_USER。检查默认组合角色不含管理或机器权限，保留既有账号自助权限；已存在用户的必需操作与凭证不会被这次升级重置。

工具仅允许 HTTPS 身份中心地址，回环地址可用 HTTP 做本地验证，不跟随重定向转发凭据。生产继续使用既有 HTTPS 入口。若暂时关闭自助注册，可以在身份中心关闭 User registration；已经创建的用户和个人空间会继续保留。

## 后续边界

注册验证和找回密码邮件经过独立邮件服务，不提供邀请邮件或自动加入共享空间。备份与容量管理应同时覆盖身份库、业务库、邮件库及邮件加密密钥。实际验证与部署事项见 [STATUS.md](STATUS.md)。
