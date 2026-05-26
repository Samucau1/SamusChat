package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.DeviceTokenRequest;
import com.chatapp.chatapp_backend.entity.UserDevice;
import com.chatapp.chatapp_backend.repository.UserDeviceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final UserDeviceRepository userDeviceRepository;

    public void registerDevice(String userEmail, DeviceTokenRequest request) {
        if (request.getFcmToken() == null || request.getFcmToken().isBlank()) {
            throw new RuntimeException("Token FCM invalido");
        }

        String token = request.getFcmToken().trim();

        userDeviceRepository.findByFcmToken(token)
                .ifPresentOrElse(
                        device -> {
                            device.setUserEmail(userEmail);
                            device.setDeviceModel(request.getDeviceModel());
                            device.setLastSeen(LocalDateTime.now());
                            userDeviceRepository.save(device);
                        },
                        () -> {
                            UserDevice device = new UserDevice();
                            device.setUserEmail(userEmail);
                            device.setFcmToken(token);
                            device.setDeviceModel(request.getDeviceModel());
                            userDeviceRepository.save(device);
                        }
                );
    }

    @Transactional
    public void unregisterDevice(String fcmToken) {
        if (fcmToken == null || fcmToken.isBlank()) {
            throw new RuntimeException("Token FCM invalido");
        }

        userDeviceRepository.deleteByFcmToken(fcmToken.trim());
    }
}
