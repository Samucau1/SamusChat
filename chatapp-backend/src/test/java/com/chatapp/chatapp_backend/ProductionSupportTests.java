package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.dto.MessageResponse;
import com.chatapp.chatapp_backend.websocket.MessageCreatedEvent;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "management.endpoints.web.exposure.include=health,info,metrics"})
@AutoConfigureMockMvc
class ProductionSupportTests {
    @Autowired private MockMvc mvc;
    @Autowired private Flyway flyway;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private ApplicationEventPublisher events;
    @MockitoBean private StringRedisTemplate redis;

    @BeforeEach
    void resetPublisher() { reset(redis); }

    @Test
    void migrationCreatesSchemaThatHibernateValidates() {
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("5");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void healthIsPublicAndNeverConsumesTheApiRateLimit() throws Exception {
        for (int request = 0; request < 65; request++) {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist());
        }
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicationWaitsUntilCommit() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(new MessageCreatedEvent(MessageResponse.builder().id(1L).channelId(2L).build()));
            verifyNoInteractions(redis);
        });
        verify(redis).convertAndSend(eq("chat:channel:2"), anyString());
    }

    @Test
    void rollbackDoesNotPublish() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(new MessageCreatedEvent(MessageResponse.builder().id(1L).channelId(2L).build()));
            status.setRollbackOnly();
        });
        verifyNoInteractions(redis);
    }
}
