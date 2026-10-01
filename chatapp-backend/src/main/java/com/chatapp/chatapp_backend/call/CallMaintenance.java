package com.chatapp.chatapp_backend.call;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Configuration @EnableScheduling @RequiredArgsConstructor
public class CallMaintenance {
    private final CallRepository calls;
    @Scheduled(initialDelay=30000, fixedDelay=30000) @Transactional
    public void clean() {
        var now=Instant.now();
        calls.expireAbandoned(now.minusSeconds(45),now.minusSeconds(105),now.minusSeconds(45));
        calls.deleteOld(now.minusSeconds(86400));
    }
}
