package com.mike.workstation.dto;

/**
 * 创建或更新笔记文件夹时使用的请求体。
 */
public record NoteFolderRequest(String name, Long parentId) {
}
