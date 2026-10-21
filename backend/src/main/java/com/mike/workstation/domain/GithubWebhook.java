package com.mike.workstation.domain;
import lombok.Data;

/** 连接级 Webhook 配置，Secret 使用现有加密密钥保护且不回显。 */
@Data
public class GithubWebhook {
    private String id;
    private Long workspaceId;
    private Long connectionId;
    private String secret;
    private String keyVersion;
    private boolean enabled;
    private int version;
}
