package com.mike.workstation.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return new CurrentUserResponse(authentication.getName(), true);
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {
    }

    public record CurrentUserResponse(String username, boolean owner) {
    }
}
