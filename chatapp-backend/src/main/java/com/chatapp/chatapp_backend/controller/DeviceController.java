package com.chatapp.chatapp_backend.controller;

import com.chatapp.chatapp_backend.dto.ApiResponse;
import com.chatapp.chatapp_backend.dto.DeviceTokenRequest;
import com.chatapp.chatapp_backend.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @org.springframework.beans.factory.annotation.Value("${app.notifications.strategy:push}")
    private String notificationStrategy;

    @org.springframework.web.bind.annotation.GetMapping("/web-push/status")
    public ApiResponse<java.util.Map<String, Boolean>> webPushStatus() {
        return ApiResponse.success(java.util.Map.of("enabled",
                "push".equals(notificationStrategy) && !com.google.firebase.FirebaseApp.getApps().isEmpty()));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> registerDevice(
            Authentication auth,
            @org.springframework.web.bind.annotation.RequestHeader("Authorization") String authorization,
            @RequestBody DeviceTokenRequest request) {
        deviceService.registerDevice(auth.getName(), request, authorization.substring(7));
        return ResponseEntity.ok(ApiResponse.success(null, "Dispositivo registrado com sucesso"));
    }

    @DeleteMapping("/unregister")
    public ResponseEntity<ApiResponse<Void>> unregisterDevice(Authentication auth, @RequestParam String fcmToken) {
        deviceService.unregisterDevice(auth.getName(), fcmToken);
        return ResponseEntity.ok(ApiResponse.success(null, "Dispositivo removido com sucesso"));
    }
}
