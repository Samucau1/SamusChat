package com.chatapp.chatapp_backend.security;

import com.chatapp.chatapp_backend.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.*;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
public class PublicIdentityAdvice implements ResponseBodyAdvice<Object> {
    private final PublicIdentityService identities;
    public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
        return org.springframework.http.converter.json.MappingJackson2HttpMessageConverter.class.isAssignableFrom(converter);
    }
    public Object beforeBodyWrite(Object body, MethodParameter method, MediaType type, Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof ApiResponse<?>)) return body;
        return identities.external(body, request.getURI().getPath().startsWith("/api/users/me"));
    }
}
