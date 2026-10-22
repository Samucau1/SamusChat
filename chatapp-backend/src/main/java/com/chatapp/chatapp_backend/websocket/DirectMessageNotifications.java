package com.chatapp.chatapp_backend.websocket;

import com.chatapp.chatapp_backend.strategy.NotificationStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.List;

/** Notification failure must never roll back a committed message or block its socket delivery. */
@Component
public class DirectMessageNotifications {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(DirectMessageNotifications.class);
    private final NotificationStrategy notifications;
    private final java.util.concurrent.Executor executor;
    public DirectMessageNotifications(@Qualifier("notificationStrategy") NotificationStrategy notifications,
            @Qualifier("directNotificationExecutor") java.util.concurrent.Executor executor) {
        this.notifications=notifications;this.executor=executor;
    }
    @TransactionalEventListener
    public void created(DirectMessageCreatedEvent event) {
        var message=event.message();
        try { executor.execute(()->{
            try { notifications.notify(List.of(message.getRecipientEmail()), "SamusChat", "Nova mensagem privada", "direct", message.getSenderEmail()); }
            catch(RuntimeException failure) { log.warn("Private notification unavailable for message {}",message.getId()); }
        }); } catch(java.util.concurrent.RejectedExecutionException full) { log.warn("Private notification queue full for message {}",message.getId()); }
    }
    @Configuration
    static class Pool {
        @Bean("directNotificationExecutor")
        ThreadPoolTaskExecutor executor() {
            var pool=new ThreadPoolTaskExecutor();pool.setCorePoolSize(1);pool.setMaxPoolSize(1);pool.setQueueCapacity(64);pool.setThreadNamePrefix("direct-notify-");
            return pool;
        }
    }
}
