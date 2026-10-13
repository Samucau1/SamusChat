package com.chatapp.chatapp_backend.exception;

import com.chatapp.chatapp_backend.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> denied(Exception ex) { return ResponseEntity.status(403).body(ApiResponse.error("Acesso negado")); }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> status(org.springframework.web.server.ResponseStatusException ex) {
        if (ex.getStatusCode().value() == 429) return ResponseEntity.status(429).header("Retry-After", "60").header("X-Rate-Limit-Retry-After", "60").body(ApiResponse.error("Muitas requisicoes. Tente novamente em 60 segundos."));
        return ResponseEntity.status(ex.getStatusCode()).body(ApiResponse.error("Solicitacao nao permitida"));
    }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(Exception ex) { return ResponseEntity.status(404).body(ApiResponse.error("Nao encontrado")); }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        log.warn("Erro de negocio: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler({org.springframework.dao.DataAccessException.class, jakarta.persistence.PersistenceException.class})
    public ResponseEntity<ApiResponse<Void>> handlePersistenceException(Exception ex) {
        return handleGenericException(ex);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurityException(SecurityException ex) {
        log.warn("Tentativa de acesso nao autorizado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Nao autorizado"));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(org.springframework.web.HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error("Formato de solicitacao nao suportado"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Erro interno nao esperado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Erro interno do servidor. Tente novamente."));
    }
}
