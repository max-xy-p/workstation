package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class MarkdownTitleExtractorTests {
    @Test
    void extractsFirstH1Heading() {
        String title =
                MarkdownTitleExtractor.extract(
                        "# Java\n\ncontent", Path.of("Java 2c49efd02756806ab31ac936854bfd54.md"));

        assertThat(title).isEqualTo("Java");
    }

    @Test
    void removesNotionIdSuffixFromFileNameWhenHeadingMissing() {
        String title =
                MarkdownTitleExtractor.extract(
                        "content", Path.of("Redis 2bc9efd0275680bdb22cf673d34e0b20.md"));

        assertThat(title).isEqualTo("Redis");
    }

    @Test
    void extractsHeadingWithUtf8Bom() {
        String title =
                MarkdownTitleExtractor.extract("\uFEFF# MySQL\n\ncontent", Path.of("MySQL.md"));

        assertThat(title).isEqualTo("MySQL");
    }
}
