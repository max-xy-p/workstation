package com.mike.workstation.controller;

import com.mike.workstation.dto.NotificationReadAllRequest;
import com.mike.workstation.dto.NotificationResponse;
import com.mike.workstation.dto.NotificationState;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.security.CurrentPrincipalResolver;
import com.mike.workstation.service.NotificationService;
import com.mike.workstation.service.NotificationStreamHub;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** HTTP 提供可靠状态，SSE 只提醒浏览器重新查询。 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    private final NotificationStreamHub streams;
    private final CurrentPrincipalResolver principals;

    public NotificationController(NotificationService service, NotificationStreamHub streams,
            CurrentPrincipalResolver principals) {
        this.service = service;
        this.streams = streams;
        this.principals = principals;
    }

    @GetMapping("/state")
    public NotificationState state() {
        return service.state();
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Long upToSequence) {
        return service.list(page, size, unreadOnly, upToSequence);
    }

    @GetMapping("/{notificationId}")
    public NotificationResponse get(@PathVariable String notificationId) {
        return service.get(notificationId);
    }

    @PutMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable String notificationId) {
        service.read(notificationId);
    }

    @PutMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@RequestBody NotificationReadAllRequest request) {
        service.readAll(request);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream() {
        String recipient = service.requireUser();
        service.requireEnabled();
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Accel-Buffering", "no")
                .body(streams.open(recipient, principals.requireExpiresAt()));
    }
}
