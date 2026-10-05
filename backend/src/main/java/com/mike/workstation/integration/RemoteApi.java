package com.mike.workstation.integration;

import com.mike.workstation.config.IntegrationProperties;
import com.mike.workstation.domain.IntegrationConnection;
import com.mike.workstation.domain.IntegrationProvider;
import com.mike.workstation.exception.BusinessException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 固定平台 API 范围且禁止重定向，错误不包含上游响应或认证头。 */
@Component
public class RemoteApi {
    private final IntegrationProperties properties;
    private final ObjectMapper json;
    private final HttpClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public RemoteApi(IntegrationProperties properties, ObjectMapper json) {
        this(properties, json, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.requestTimeoutSeconds()))).build());
    }

    RemoteApi(IntegrationProperties properties, ObjectMapper json, HttpClient client) {
        this.properties = properties;
        this.json = json;
        this.client = client;
    }

    public String normalize(IntegrationProvider provider, String value) {
        if (provider == IntegrationProvider.GITHUB) {
            if (!"https://github.com".equals(value) && !"https://github.com/".equals(value))
                throw new BusinessException("GitHub 数据源仅支持 https://github.com");
            return "https://github.com";
        }
        String normalized = normalizeInstance(value);
        if (properties.allowedGitlabInstances() == null || properties.allowedGitlabInstances().stream()
                .map(this::normalizeInstance).noneMatch(normalized::equals))
            throw new BusinessException("GitLab 实例未列入部署允许范围");
        return normalized;
    }

    private String normalizeInstance(String value) {
        URI uri;
        try { uri = URI.create(value); } catch (RuntimeException ex) { throw new BusinessException("平台实例地址不合法"); }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null || uri.getPort() == 0
                || uri.getPath().contains("..") || uri.getPath().contains("%"))
            throw new BusinessException("平台实例必须使用 HTTPS 且不能包含凭证或查询参数");
        String path = uri.getPath().replaceAll("/+$", "");
        return "https://" + uri.getHost().toLowerCase(Locale.ROOT)
                + (uri.getPort() < 0 || uri.getPort() == 443 ? "" : ":" + uri.getPort()) + path;
    }

    public JsonNode get(IntegrationConnection connection, String token, String path) {
        String normalized = normalize(IntegrationProvider.valueOf(connection.getProvider()), connection.getBaseUrl());
        if (!path.startsWith("/") || path.startsWith("//") || java.util.Arrays.stream(URI.create(path).getPath().split("/")).anyMatch(".."::equals) || path.contains("\\"))
            throw new BusinessException("平台接口路径不合法");
        String base = "GITHUB".equals(connection.getProvider()) ? "https://api.github.com" : normalized + "/api/v4";
        URI target = URI.create(base + path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(Math.max(1, properties.requestTimeoutSeconds())))
                .header("Accept", "application/json").header("User-Agent", "Workstation").GET();
        if ("GITHUB".equals(connection.getProvider())) {
            builder.header("Authorization", "Bearer " + token).header("X-GitHub-Api-Version", "2026-03-10");
        } else {
            builder.header("PRIVATE-TOKEN", token);
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status == 200) {
                    if (response.body().length() > 2_000_000) throw new BusinessException("平台响应超过允许大小");
                    return json.readTree(response.body());
                }
                if (status == 401) throw new BusinessException("平台凭证失效，请重新配置");
                boolean limited = status == 429 || (status == 403 &&
                        (response.headers().firstValue("Retry-After").isPresent()
                                || response.headers().firstValue("X-RateLimit-Remaining").orElse("1").equals("0")));
                if (!limited && status < 500) throw new BusinessException("平台资源不可读取，请检查权限和仓库状态");
                if (attempt == 2) throw new BusinessException(limited ? "平台请求受限，请稍后重试" : "平台暂时不可用");
                Thread.sleep(1000L << attempt);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BusinessException("平台请求已中止");
            } catch (IOException ex) {
                if (attempt == 2) throw new BusinessException("无法连接平台，请检查实例与网络");
            }
        }
        throw new BusinessException("平台请求失败");
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
