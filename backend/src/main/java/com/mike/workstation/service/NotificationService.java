package com.mike.workstation.service;

import com.mike.workstation.assembler.NotificationResponseAssembler;
import com.mike.workstation.config.NotificationProperties;
import com.mike.workstation.domain.PrincipalType;
import com.mike.workstation.dto.NotificationReadAllRequest;
import com.mike.workstation.dto.NotificationResponse;
import com.mike.workstation.dto.NotificationState;
import com.mike.workstation.dto.PageResponse;
import com.mike.workstation.exception.BusinessException;
import com.mike.workstation.exception.NotFoundException;
import com.mike.workstation.exception.WorkspaceAccessDeniedException;
import com.mike.workstation.mapper.NotificationMapper;
import com.mike.workstation.security.CurrentPrincipalResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** 通知属于用户，访问空间内容的资格仍由当前成员关系决定。 */
@Service
public class NotificationService {
    private final NotificationMapper mapper;
    private final CurrentPrincipalResolver principals;
    private final NotificationProperties properties;

    public NotificationService(NotificationMapper mapper, CurrentPrincipalResolver principals,
            NotificationProperties properties) {
        this.mapper = mapper;
        this.principals = principals;
        this.properties = properties;
    }

    public String requireUser() {
        var principal = principals.require();
        if (principal.principalType() != PrincipalType.USER) {
            throw new WorkspaceAccessDeniedException("站内通知仅供用户账号使用");
        }
        return principal.subjectId();
    }

    public NotificationState state() {
        String recipient = requireUser();
        return properties.enabled() ? mapper.state(recipient) : new NotificationState(false, 0, 0, 0);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<NotificationResponse> list(int page, int size, boolean unreadOnly, Long upper) {
        String recipient = requireUser();
        if (page < 0 || size < 1 || size > 100 || upper != null && upper < 0) {
            throw new BusinessException("通知分页参数不合法");
        }
        if (!properties.enabled()) {
            return PageResponse.of(List.of(), page, size, 0);
        }
        long latest = mapper.state(recipient).latestSequence();
        long bound = upper == null ? latest : Math.min(latest, upper);
        return PageResponse.of(mapper.list(recipient, bound, unreadOnly, (long) page * size, size).stream()
                .map(NotificationResponseAssembler::toResponse).toList(), page, size,
                mapper.count(recipient, bound, unreadOnly));
    }

    public NotificationResponse get(String id) {
        String recipient = requireUser();
        requireEnabled();
        var notification = mapper.find(recipient, NotificationCodec.canonicalId(id));
        if (notification == null) {
            throw new NotFoundException("通知不存在");
        }
        return NotificationResponseAssembler.toResponse(notification);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void read(String id) {
        String recipient = requireUser();
        requireEnabled();
        mapper.lockInbox(recipient);
        if (mapper.find(recipient, NotificationCodec.canonicalId(id)) == null) {
            throw new NotFoundException("通知不存在");
        }
        if (mapper.read(recipient, id, now()) > 0) {
            mapper.changed(recipient);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void readAll(NotificationReadAllRequest request) {
        String recipient = requireUser();
        requireEnabled();
        if (request == null || request.throughSequence() == null || request.throughSequence() < 0) {
            throw new BusinessException("全部已读需要有效的通知序号上界");
        }
        Long latest = mapper.lockInbox(recipient);
        if (latest == null) {
            return;
        }
        long upper = Math.min(request.throughSequence(), latest);
        if (mapper.readAll(recipient, upper, now()) > 0) {
            mapper.changed(recipient);
        }
    }

    public void requireEnabled() {
        if (!properties.enabled()) {
            throw new NotFoundException("通知中心尚未启用");
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }
}
