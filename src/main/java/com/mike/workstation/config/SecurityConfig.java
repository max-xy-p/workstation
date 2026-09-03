package com.mike.workstation.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(SecurityProperties properties) {
        var owner = User.withUsername(properties.adminUsername())
                .password(properties.adminPasswordHash())
                .roles("OWNER")
                .build();
        return new InMemoryUserDetailsManager(owner);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .failureHandler((request, response, exception) -> writeError(
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "AUTHENTICATION_FAILED",
                                "用户名或密码错误")))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "UNAUTHENTICATED",
                                "请先登录"))
                        .accessDeniedHandler((request, response, exception) -> writeError(
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                "ACCESS_DENIED",
                                "请求校验失败，请刷新后重试")))
                .requestCache(cache -> cache.disable());

        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().printf("{\"code\":\"%s\",\"message\":\"%s\"}", code, message);
    }
}
