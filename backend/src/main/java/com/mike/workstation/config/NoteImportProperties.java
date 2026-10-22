package com.mike.workstation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 笔记导入配置。
 *
 * @param allowedRoots 允许导入 Markdown 的本机根目录白名单
 * @param maxFileBytes 单个 Markdown 文件最大允许字节数
 */
@ConfigurationProperties(prefix = "workstation.note-import")
public record NoteImportProperties(List<String> allowedRoots, Long maxFileBytes) {}
