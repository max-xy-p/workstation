package com.mike.workstation.service;

import java.nio.file.Path;
import java.util.regex.Pattern;

final class MarkdownTitleExtractor {
    private static final Pattern NOTION_FILE_ID_SUFFIX = Pattern.compile("\\s+[0-9a-fA-F]{32}$");

    private MarkdownTitleExtractor() {
    }

    static String extract(String content, Path path) {
        String heading = content.lines()
                .map(String::trim)
                .map(line -> line.startsWith("\uFEFF") ? line.substring(1).trim() : line)
                .filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2).trim())
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(null);
        if (heading != null) {
            return heading;
        }

        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String title = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        return NOTION_FILE_ID_SUFFIX.matcher(title).replaceFirst("").trim();
    }
}
