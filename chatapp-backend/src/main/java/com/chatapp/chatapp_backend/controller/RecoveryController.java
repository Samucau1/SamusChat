package com.chatapp.chatapp_backend.controller;
import com.chatapp.chatapp_backend.dto.ApiResponse;
import com.chatapp.chatapp_backend.service.PasswordRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/auth/password") @RequiredArgsConstructor
public class RecoveryController {
    private final PasswordRecoveryService recovery;
    public record EmailRequest(String email) {}
    public record VerifyRequest(String email,String code) {}
    public record ResetRequest(String email,String resetToken,String password) {}
    @PostMapping("/request") public ApiResponse<String> request(@RequestBody EmailRequest r) {
        recovery.request(r.email()); return ApiResponse.success("Se a conta existir, voce recebera um codigo. Aguarde 10 minutos para reenviar.");
    }
    @PostMapping("/verify") public ApiResponse<Map<String,String>> verify(@RequestBody VerifyRequest r) {
        return ApiResponse.success(Map.of("resetToken",recovery.verify(r.email(),r.code())));
    }
    @PostMapping("/reset") public ApiResponse<String> reset(@RequestBody ResetRequest r) {
        recovery.reset(r.email(),r.resetToken(),r.password()); return ApiResponse.success("Senha atualizada. Entre com sua nova senha.");
    }
}
