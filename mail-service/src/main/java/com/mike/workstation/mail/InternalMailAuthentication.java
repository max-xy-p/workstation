package com.mike.workstation.mail;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalMailAuthentication extends OncePerRequestFilter {
    public static final String CALLER = "mail.caller";
    private final MailProperties properties;

    public InternalMailAuthentication(MailProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("GET".equals(request.getMethod()) && "/health".equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        if ("POST".equals(request.getMethod()) && "/hooks/resend".equals(request.getRequestURI())) {
            // 此准确路径由控制器校验原文签名；不接受 Session 或内部调用凭据替代签名。
            chain.doFilter(request, response);
            return;
        }
        if (request.getContentLengthLong() > 512000) {
            response.sendError(413);
            return;
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : "";
        boolean keycloak = matches(token, properties.keycloakToken());
        boolean backend = matches(token, properties.backendToken());
        if (!keycloak && !backend) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"邮件服务身份验证失败\"}");
            return;
        }
        request.setAttribute(CALLER, keycloak ? "keycloak" : "backend");
        chain.doFilter(request, response);
    }

    private boolean matches(String value, String expected) {
        return MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
