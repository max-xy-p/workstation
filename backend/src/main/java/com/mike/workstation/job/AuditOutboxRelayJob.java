package com.mike.workstation.job;

import com.mike.workstation.service.AuditOutboxRelay;
import com.mike.workstation.service.AuditRelayResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

import org.springframework.stereotype.Component;

/**
 * 由 XXL-JOB 触发的审计 Outbox 批量投递任务。
 *
 * <p>XXL-JOB 只决定“何时执行”和记录调度日志；领取、重试、租约及失败状态仍由 Backend 管理， 因此调度中心短暂不可用只会造成 Outbox 积压，不影响正常业务提交。
 */
@Component
public class AuditOutboxRelayJob {
    private final AuditOutboxRelay relay;

    public AuditOutboxRelayJob(AuditOutboxRelay relay) {
        this.relay = relay;
    }

    @XxlJob("auditOutboxRelayJob")
    public void execute() {
        // 每次只处理配置的 batch-size，避免积压恢复时单次任务占用线程过久。
        AuditRelayResult result = relay.relayBatch();
        XxlJobHelper.log(
                "审计 Outbox：领取 {} 条，发布成功 {} 条，等待重试 {} 条",
                result.claimed(),
                result.published(),
                result.failed());
    }
}
