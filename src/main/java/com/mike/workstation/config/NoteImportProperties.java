package com.mike.workstation.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workstation.note-import")
public record NoteImportProperties(List<String> allowedRoots, Long maxFileBytes) {
}
