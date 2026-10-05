package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.controller.GoogleAuthController;
import com.chatapp.chatapp_backend.exception.GlobalExceptionHandler;
import com.chatapp.chatapp_backend.service.GoogleAuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GoogleGisCsrfTests {
    GoogleAuthService auth;
    MockMvc mvc;
    @BeforeEach void setup() {
        auth=mock(GoogleAuthService.class);
        mvc=MockMvcBuilders.standaloneSetup(new GoogleAuthController(auth))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void matchingJsonTokensAllowGoogleVerification() throws Exception {
        when(auth.login("google-token",null)).thenReturn("session");
        mvc.perform(post("/api/auth/google/gis").contentType("application/json")
            .cookie(new Cookie("g_csrf_token","csrf"))
            .content("{\"credential\":\"google-token\",\"g_csrf_token\":\"csrf\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").value("session"));
        verify(auth).login("google-token",null);
    }
    @Test void missingMismatchedEmptyAndDuplicateCookiesNeverVerifyGoogleToken() throws Exception {
        var body="{\"credential\":\"google-token\",\"g_csrf_token\":\"csrf\"}";
        mvc.perform(post("/api/auth/google/gis").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        for(String cookie:new String[]{"wrong","","CSRF","csrf "})
            mvc.perform(post("/api/auth/google/gis").contentType("application/json")
                .cookie(new Cookie("g_csrf_token",cookie)).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/google/gis").contentType("application/json")
            .cookie(new Cookie("g_csrf_token","csrf"),new Cookie("g_csrf_token","csrf")).content(body)).andExpect(status().isUnauthorized());
        for(String missingBody:new String[]{"{\"credential\":\"google-token\"}","{\"credential\":\"google-token\",\"g_csrf_token\":\"\"}"})
            mvc.perform(post("/api/auth/google/gis").contentType("application/json")
                .cookie(new Cookie("g_csrf_token","csrf")).content(missingBody)).andExpect(status().isUnauthorized());
        verifyNoInteractions(auth);
    }
    @Test void formRequiresExactlyOneBodyTokenAndRejectsQueryOnlyToken() throws Exception {
        when(auth.login("google-token",null)).thenReturn("session");
        mvc.perform(post("/api/auth/google/gis").contentType("application/x-www-form-urlencoded")
            .cookie(new Cookie("g_csrf_token","csrf")).content("credential=google-token&g_csrf_token=csrf"))
            .andExpect(status().isOk());
        verify(auth).login("google-token",null); clearInvocations(auth);
        for(String body:new String[]{"credential=google-token", "credential=google-token&g_csrf_token=wrong", "credential=google-token&g_csrf_token=csrf&g_csrf_token=csrf"})
            mvc.perform(post("/api/auth/google/gis?g_csrf_token=csrf").contentType("application/x-www-form-urlencoded")
                .cookie(new Cookie("g_csrf_token","csrf")).content(body)).andExpect(status().isUnauthorized());
        verifyNoInteractions(auth);
    }
    @Test void validCsrfStillRejectsInvalidGoogleToken() throws Exception {
        when(auth.login("invalid",null)).thenThrow(new IllegalArgumentException("Token Google invalido"));
        mvc.perform(post("/api/auth/google/gis").contentType("application/json")
            .cookie(new Cookie("g_csrf_token","csrf")).content("{\"credential\":\"invalid\",\"g_csrf_token\":\"csrf\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test void nativeJsonRemainsCompatibleAndRejectsBrowserForm() throws Exception {
        when(auth.login("native-token",null)).thenReturn("session");
        mvc.perform(post("/api/auth/google").contentType("application/json").content("{\"idToken\":\"native-token\"}"))
            .andExpect(status().isOk());
        verify(auth).login("native-token",null); clearInvocations(auth);
        mvc.perform(post("/api/auth/google").contentType("application/x-www-form-urlencoded")
            .content("idToken=native-token")).andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(auth);
    }
}
