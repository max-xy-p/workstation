package com.mike.workstation.controller;

import com.mike.workstation.dto.DashboardResponse;
import com.mike.workstation.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
/**
 * 工作台概览接口控制器。
 *
 * <p>首页只需要聚合统计和最近更新列表，因此单独放在 Dashboard 接口中，避免前端多次请求再拼装。</p>
 */
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 查询首页概览数据。
     *
     * @return 活跃项目数、待办任务数、进行中任务数、最近项目和最近笔记
     */
    @GetMapping
    public DashboardResponse summary() {
        return dashboardService.summary();
    }
}
