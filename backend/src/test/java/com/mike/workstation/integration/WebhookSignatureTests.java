package com.mike.workstation.integration;
import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebhookSignatureTests {
    @Test void githubOfficialVectorAndBodyTampering() {
        byte[] body="Hello, World!".getBytes(StandardCharsets.UTF_8);
        String signature="sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";
        assertThat(WebhookSignature.valid("It's a Secret to Everybody",body,signature)).isTrue();
        assertThat(WebhookSignature.valid("wrong",body,signature)).isFalse();
        assertThat(WebhookSignature.valid("It's a Secret to Everybody","Hello, World?".getBytes(StandardCharsets.UTF_8),signature)).isFalse();
        for(String invalid:new String[]{null,"sha1=abc","sha256="+"g".repeat(64),"sha256=00"}) assertThat(WebhookSignature.valid("test",body,invalid)).isFalse();
        assertThat(WebhookSignature.hash(body)).hasSize(64);
    }
}
