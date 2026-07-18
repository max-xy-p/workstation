package com.mike.workstation.dto;

/**
 * 创建或更新分类时使用的请求体。
 */
public record CategoryRequest(String name, String description) {
}
