package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.config.NotificationConfig;
import com.chatapp.chatapp_backend.strategy.NotificationStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationConfigTests {
    private final NotificationStrategy push = mock(NotificationStrategy.class);
    private final NotificationStrategy log = mock(NotificationStrategy.class);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(NotificationConfig.class)
            .withBean("pushStrategy", NotificationStrategy.class, () -> push)
            .withBean("logStrategy", NotificationStrategy.class, () -> log);

    @Test
    void defaultsToPushToPreserveAndroidNotifications() {
        context.run(app -> assertThat(app.getBean("notificationStrategy")).isSameAs(push));
    }

    @Test
    void selectsLogWithoutChangingTheService() {
        context.withPropertyValues("app.notifications.strategy=log")
                .run(app -> assertThat(app.getBean("notificationStrategy")).isSameAs(log));
    }

    @Test
    void rejectsAnUnknownStrategyAtStartup() {
        context.withPropertyValues("app.notifications.strategy=unknown")
                .run(app -> assertThat(app.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("app.notifications.strategy deve ser push ou log"));
    }
}
