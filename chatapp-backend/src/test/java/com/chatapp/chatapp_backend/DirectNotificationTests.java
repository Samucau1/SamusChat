package com.chatapp.chatapp_backend;
import com.chatapp.chatapp_backend.entity.DirectMessage;
import com.chatapp.chatapp_backend.strategy.NotificationStrategy;
import com.chatapp.chatapp_backend.websocket.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
class DirectNotificationTests {
    @Test void genericNotificationTargetsRecipientOnlyAndFailureIsIsolated() {
        var strategy=mock(NotificationStrategy.class);var listener=new DirectMessageNotifications(strategy,Runnable::run);
        var message=new DirectMessage();message.setSenderEmail("sender@test.com");message.setRecipientEmail("recipient@test.com");message.setContent("private body");
        listener.created(new DirectMessageCreatedEvent(message));
        verify(strategy).notify(List.of("recipient@test.com"),"SamusChat","Nova mensagem privada","direct","sender@test.com");
        doThrow(new IllegalStateException()).when(strategy).notify(anyList(),anyString(),anyString(),anyString(),anyString());
        listener.created(new DirectMessageCreatedEvent(message));
    }
}
