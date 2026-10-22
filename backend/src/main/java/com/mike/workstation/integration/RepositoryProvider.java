package com.mike.workstation.integration;

import com.mike.workstation.domain.DevelopmentItem;
import com.mike.workstation.domain.DevelopmentKind;
import com.mike.workstation.domain.ExternalRepository;
import com.mike.workstation.domain.IntegrationConnection;
import com.mike.workstation.exception.BusinessException;

import java.time.LocalDateTime;
import java.util.List;

/** 平台差异限制在仓库读取与开发情况适配层。 */
public interface RepositoryProvider {
    String provider();

    default DevelopmentItem issue(
            IntegrationConnection connection,
            String token,
            ExternalRepository repository,
            long number) {
        throw new BusinessException("当前平台不支持 Issue 定点跟踪");
    }

    String account(IntegrationConnection connection, String token);

    List<ExternalRepository> repositories(IntegrationConnection connection, String token, int page);

    ExternalRepository repository(
            IntegrationConnection connection, String token, ExternalRepository repository);

    List<DevelopmentItem> development(
            IntegrationConnection connection,
            String token,
            ExternalRepository repository,
            DevelopmentKind kind,
            LocalDateTime since,
            int page);
}
