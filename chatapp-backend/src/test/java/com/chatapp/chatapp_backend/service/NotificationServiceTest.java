package com.chatapp.chatapp_backend.service;

import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceTest {
    @Test
    void directPushUsesPublicRoutingAndOneClientControlledNotification() throws Exception {
        var message = NotificationService.notificationMessage("device-token", "direct", "usr_1_abcdefghijklmnopqrstuv", "usr_2_abcdefghijklmnopqrstuv");
        var payload = JsonParser.parseString(GsonFactory.getDefaultInstance().toString(message)).getAsJsonObject();
        var data = payload.getAsJsonObject("data");
        assertThat(data.get("type").getAsString()).isEqualTo("NEW_MESSAGE");
        assertThat(data.get("recipientId").getAsString()).isEqualTo("usr_1_abcdefghijklmnopqrstuv");
        assertThat(data.get("contactId").getAsString()).isEqualTo("usr_2_abcdefghijklmnopqrstuv");
        assertThat(data.get("body").getAsString()).isEqualTo("Você tem uma nova mensagem.");
        assertThat(payload.has("notification")).isFalse();
        assertThat(payload.getAsJsonObject("android").has("notification")).isFalse();
        assertThat(payload.getAsJsonObject("webpush").has("notification")).isFalse();
        assertThat(payload.getAsJsonObject("webpush").getAsJsonObject("headers").get("TTL").getAsString()).isEqualTo("300");
    }

    @Test
    void channelPushUsesChannelRoutingWithoutAnotherUsersContact() throws Exception {
        var message = NotificationService.notificationMessage("device-token", "42", "usr_1_abcdefghijklmnopqrstuv", "sender@example.test");
        var payload = JsonParser.parseString(GsonFactory.getDefaultInstance().toString(message)).getAsJsonObject().getAsJsonObject("data");
        assertThat(payload.get("channelId").getAsString()).isEqualTo("42");
        assertThat(payload.has("contactId")).isFalse();
        assertThat(payload.toString()).doesNotContain("sender@example.test");
    }
}
