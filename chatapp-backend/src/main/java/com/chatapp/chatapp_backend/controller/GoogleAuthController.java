package com.chatapp.chatapp_backend.controller;

import com.chatapp.chatapp_backend.dto.ApiResponse;
import com.chatapp.chatapp_backend.service.GoogleAuthService;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class GoogleAuthController {
    private final GoogleAuthService auth;
    public record GoogleRequest(String idToken,String password) {}
    public record GisRequest(String credential, @JsonProperty("g_csrf_token") String csrfToken, String password) {}

    // Native Credential Manager sends JSON and does not authenticate using cookies.
    @PostMapping(value="/google", consumes=MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String,String>> login(@RequestBody GoogleRequest r) {
        return authenticated(r.idToken(),r.password());
    }

    @PostMapping(value="/google/gis", consumes=MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String,String>> gisJson(@RequestBody GisRequest r, HttpServletRequest request) {
        validateCsrf(request,r.csrfToken());
        return authenticated(r.credential(),r.password());
    }

    @PostMapping(value="/google/gis", consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ApiResponse<Map<String,String>> gisForm(@RequestBody org.springframework.util.MultiValueMap<String,String> body, HttpServletRequest request) {
        var csrf=body.get("g_csrf_token");
        if(csrf==null || csrf.size()!=1) throw new SecurityException("CSRF invalido");
        validateCsrf(request,csrf.get(0));
        return authenticated(body.getFirst("credential"),body.getFirst("password"));
    }

    private ApiResponse<Map<String,String>> authenticated(String token,String password) {
        return ApiResponse.success(Map.of("token",auth.login(token,password)));
    }

    private void validateCsrf(HttpServletRequest request,String bodyToken) {
        String cookieToken=null;
        if(request.getCookies()!=null) for(var cookie:request.getCookies()) {
            if("g_csrf_token".equals(cookie.getName())) {
                if(cookieToken!=null) throw new SecurityException("CSRF invalido");
                cookieToken=cookie.getValue();
            }
        }
        if(cookieToken==null || cookieToken.isBlank() || bodyToken==null || bodyToken.isBlank()
            || !MessageDigest.isEqual(cookieToken.getBytes(StandardCharsets.UTF_8),bodyToken.getBytes(StandardCharsets.UTF_8)))
            throw new SecurityException("CSRF invalido");
    }
}
