package com.chatapp.chatapp_backend.call;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class IceConfigurationTest {
    @Test void credentialsAreExpiringAndDoNotExposeSharedSecret() {
        var config=new IceConfiguration();
        ReflectionTestUtils.setField(config,"stun","");
        ReflectionTestUtils.setField(config,"turn","turn:example.test:3478");
        ReflectionTestUtils.setField(config,"secret","test-secret");
        var first=config.servers("first@example.test").getFirst();
        var second=config.servers("second@example.test").getFirst();
        assertThat(first.credential()).isNotEqualTo("test-secret").isNotEqualTo(second.credential());
        assertThat(first.username()).doesNotContain("first@example.test");
        long expiry=Long.parseLong(first.username().split(":")[0]);
        assertThat(expiry).isBetween(java.time.Instant.now().getEpochSecond()+3590,java.time.Instant.now().getEpochSecond()+3600);
    }
}
