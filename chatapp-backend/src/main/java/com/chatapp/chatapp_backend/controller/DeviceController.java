package com.chatapp.chatapp_backend.controller;

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

import java.util.Map;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registerDevice(
            Authentication auth,
            @RequestBody DeviceTokenRequest request) {
        deviceService.registerDevice(auth.getName(), request);
        return ResponseEntity.ok(Map.of("message", "Dispositivo registrado com sucesso"));
    }

    @DeleteMapping("/unregister")
    public ResponseEntity<Map<String, String>> unregisterDevice(@RequestParam String fcmToken) {
        deviceService.unregisterDevice(fcmToken);
        return ResponseEntity.ok(Map.of("message", "Dispositivo removido com sucesso"));
    }
}
