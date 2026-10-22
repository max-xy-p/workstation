package com.mike.workstation.service;

import com.mike.workstation.domain.AuditOutbox;
import com.mike.workstation.mapper.AuditOutboxMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 分别以现有业务事务和独立事务持久化成功、拒绝审计事件。 */
@Service
public class AuditOutboxWriter {
    private final AuditOutboxMapper outboxMapper;

    public AuditOutboxWriter(AuditOutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    /**
     * 成功业务必须已经处于事务中。
     *
     * <p>MANDATORY 不会创建新事务，而是强制复用调用方事务。因此业务表和 Outbox 要么一起提交， 要么一起回滚；如果调用方忘记开启事务，会立即报错而不是悄悄破坏一致性。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void writeRequired(AuditOutbox event) {
        outboxMapper.insert(event);
    }

    /** 权限拒绝时没有成功业务事务可复用，因此开启一个独立事务尽力记录拒绝事件。 该方法的异常会由 AuditService 吞掉，不能把原本的 403/404 改写成 500。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeNewTransaction(AuditOutbox event) {
        outboxMapper.insert(event);
    }
}
