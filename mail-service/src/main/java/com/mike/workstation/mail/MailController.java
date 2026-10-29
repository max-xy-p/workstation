package com.mike.workstation.mail;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MailController {
    private final MailService service;

    public MailController(MailService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/internal/mail/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MailStatus enqueue(@RequestAttribute(InternalMailAuthentication.CALLER) String caller,
                              @Valid @RequestBody MailRequest request) {
        return service.enqueue(caller, request);
    }

    @GetMapping("/internal/mail/messages/{id}")
    public MailStatus status(@RequestAttribute(InternalMailAuthentication.CALLER) String caller,
                             @PathVariable String id) {
        return service.status(caller, id);
    }
}
