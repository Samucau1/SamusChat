package com.chatapp.chatapp_backend.security;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="RUN_REDIS_TESTS",matches="true")
class AccountLoginLimiterRedisTests {
    @Test void concurrentAttemptsAcrossTwoInstancesShareOneAtomicLimit() throws Exception {
        var factory=new LettuceConnectionFactory("127.0.0.1",Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT","6379")));
        factory.afterPropertiesSet();factory.start();
        var redis=new StringRedisTemplate(factory);var beans=new StaticListableBeanFactory();beans.addBean("redis",redis);
        var first=new AccountLoginLimiter(beans.getBeanProvider(StringRedisTemplate.class));
        var second=new AccountLoginLimiter(beans.getBeanProvider(StringRedisTemplate.class));
        String email=UUID.randomUUID()+"@example.test";var allowed=new AtomicInteger();
        var pool=Executors.newFixedThreadPool(6);
        try {
            var jobs=new ArrayList<Future<?>>();
            for(int i=0;i<12;i++){var limiter=i%2==0?first:second;jobs.add(pool.submit(()->{try{limiter.attempt(email);allowed.incrementAndGet();}catch(ResponseStatusException ex){assertThat(ex.getStatusCode().value()).isEqualTo(429);}}));}
            for(var job:jobs)job.get(10,TimeUnit.SECONDS);
            assertThat(allowed.get()).isEqualTo(5);first.success(email);
            assertThatCode(()->second.attempt(email)).doesNotThrowAnyException();
        } finally { pool.shutdownNow();first.success(email);factory.destroy(); }
    }
}
