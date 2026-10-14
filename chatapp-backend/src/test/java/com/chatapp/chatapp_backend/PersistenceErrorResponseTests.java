package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

class PersistenceErrorResponseTests {
    @RestController static class FailingController {
        @GetMapping("/persistence-failure") public Object fail() {
            throw new InvalidDataAccessResourceUsageException("Table USERS not found; select password from users [42104-232]");
        }
    }
    @Test void databaseFailureReturnsGeneric500WithoutSqlOrTableNames() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/persistence-failure")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Erro interno do servidor. Tente novamente."))
            .andExpect(content().string(not(containsString("USERS"))))
            .andExpect(content().string(not(containsString("select"))))
            .andExpect(content().string(not(containsString("42104"))));
    }
}
