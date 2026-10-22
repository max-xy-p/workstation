package com.mike.workstation.controller;

import com.mike.workstation.dto.GithubWebhookDeliveryResponse;
import com.mike.workstation.dto.GithubWebhookRequest;
import com.mike.workstation.dto.GithubWebhookResponse;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.service.GithubWebhookService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/** 专用回调只读有限原文字节；配置与查询仍使用普通业务认证。 */
@RestController
public class GithubWebhookController {
    private final GithubWebhookService service;

    public GithubWebhookController(GithubWebhookService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/api/hooks/github/{endpointId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void receive(
            @PathVariable String endpointId,
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Delivery") String delivery,
            @RequestHeader("X-GitHub-Event") String event,
            HttpServletRequest request)
            throws IOException {
        byte[] body = request.getInputStream().readNBytes(GithubWebhookService.MAX_BODY_BYTES + 1);
        if (body.length > GithubWebhookService.MAX_BODY_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook 原文过大");
        }
        service.receive(endpointId, delivery, event, signature, body);
    }

    @GetMapping("/api/workspaces/{workspaceId}/integrations/{id}/webhook")
    public GithubWebhookResponse get(@PathVariable Long workspaceId, @PathVariable Long id) {
        return service.get(workspaceId, id);
    }

    @PutMapping("/api/workspaces/{workspaceId}/integrations/{id}/webhook")
    public GithubWebhookResponse configure(
            @PathVariable Long workspaceId,
            @PathVariable Long id,
            @RequestBody GithubWebhookRequest request) {
        return service.configure(workspaceId, id, request);
    }

    @GetMapping("/api/workspaces/{workspaceId}/integrations/{id}/webhook/deliveries")
    public PageResponse<GithubWebhookDeliveryResponse> deliveries(
            @PathVariable Long workspaceId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.deliveries(workspaceId, id, page, size);
    }

    @PostMapping(
            "/api/workspaces/{workspaceId}/integrations/{id}/webhook/deliveries/{deliveryId}/retry")
    public GithubWebhookDeliveryResponse retry(
            @PathVariable Long workspaceId, @PathVariable Long id, @PathVariable Long deliveryId) {
        return service.retry(workspaceId, id, deliveryId);
    }
}
