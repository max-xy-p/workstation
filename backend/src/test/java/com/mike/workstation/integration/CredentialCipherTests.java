package com.mike.workstation.integration;

import static org.assertj.core.api.Assertions.*;
import com.mike.workstation.config.IntegrationProperties;
import com.mike.workstation.exception.BusinessException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialCipherTests {
    static IntegrationProperties properties() {
        return new IntegrationProperties(true,"v1",Map.of("v1",Base64.getEncoder().encodeToString(new byte[32])),
                List.of("https://gitlab.com","https://gitlab.example.test/team"),15,10,300,180,30);
    }
    @Test void roundTripUsesRandomNonceAndRejectsOtherWorkspace() {
        CredentialCipher cipher=new CredentialCipher(properties());
        String a=cipher.encrypt("测试凭证","9:GITHUB"),b=cipher.encrypt("测试凭证","9:GITHUB");
        assertThat(a).isNotEqualTo(b).doesNotContain("测试凭证");
        assertThat(cipher.decrypt(a,"v1","9:GITHUB")).isEqualTo("测试凭证");
        assertThatThrownBy(()->cipher.decrypt(a,"v1","10:GITHUB")).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsTamperingMissingVersionAndInvalidCiphertext() {
        CredentialCipher cipher=new CredentialCipher(properties());
        String encrypted=cipher.encrypt("token","context");
        assertThatThrownBy(()->cipher.decrypt(encrypted+"x","v1","context")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->cipher.decrypt(encrypted,"v0","context")).hasMessageContaining("密钥");
        for(String invalid:List.of("bad","bad.parts","YQ==.Yg=="))
            assertThatThrownBy(()->cipher.decrypt(invalid,"v1","context")).isInstanceOf(BusinessException.class);
    }
    @Test void validatesKeysAndRuntimeBounds() {
        for(String key:List.of("","not base64",Base64.getEncoder().encodeToString(new byte[16]))) {
            var p=new IntegrationProperties(true,"v1",Map.of("v1",key),List.of(),15,10,100,180,30);
            assertThatThrownBy(()->new CredentialCipher(p).encrypt("token","context")).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(()->new IntegrationProperties(true,"v1",Map.of(),List.of(),15,0,10,180,30))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
