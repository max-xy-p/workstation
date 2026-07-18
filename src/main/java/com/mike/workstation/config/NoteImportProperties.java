package com.mike.workstation.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workstation.note-import")
/**
 * 笔记导入配置。
 *
 * @param allowedRoots 允许导入 Markdown 的本机根目录白名单
 * @param maxFileBytes 单个 Markdown 文件最大允许字节数
 */
public record NoteImportProperties(List<String> allowedRoots, Long maxFileBytes) {
}
