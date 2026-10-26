package com.mike.workstation.integration;

import com.mike.workstation.domain.DevelopmentItem;
import com.mike.workstation.domain.ExternalRepository;
import com.mike.workstation.domain.IntegrationRun;

import lombok.Data;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** 有界分页中间结果只存于 MySQL 运行记录，完成快照与检查点在同一事务提交。 */
@Data
public class IntegrationProgress {
    private static final ObjectMapper JSON = new ObjectMapper();
    private boolean discoveryDone;
    private int discoveryPage = 1;
    private boolean syncDone;
    private long repositoryCursor;
    private ExternalRepository repository;
    private int kindIndex;
    private int page = 1;
    private String since;
    private List<DevelopmentItem> items = new ArrayList<>();

    public static IntegrationProgress read(IntegrationRun run) {
        return run.getProgressJson() == null
                ? new IntegrationProgress()
                : JSON.readValue(run.getProgressJson(), IntegrationProgress.class);
    }

    public void store(IntegrationRun run) {
        String value = JSON.writeValueAsString(this);
        if (value.length() > 2_000_000) {
            throw new IllegalStateException("采集中间结果超过允许大小");
        }
        run.setProgressJson(value);
    }

    public void nextKind() {
        kindIndex++;
        page = 1;
        since = null;
        items = new ArrayList<>();
    }

    public void nextRepository(long id) {
        repositoryCursor = id;
        repository = null;
        kindIndex = 0;
        page = 1;
        since = null;
        items = new ArrayList<>();
    }
}
