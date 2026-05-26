package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.repository.UserDeviceRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final UserDeviceRepository userDeviceRepository;

    public void sendToUser(String userEmail, String title, String body, String channelId) {
        List<String> tokens = userDeviceRepository
                .findByUserEmail(userEmail)
                .stream()
                .map(device -> device.getFcmToken())
                .collect(Collectors.toList());

        if (tokens.isEmpty()) {
            log.debug("Usuario {} nao tem dispositivos registrados", userEmail);
            return;
        }

        for (String token : tokens) {
            sendNotification(token, title, body, channelId);
        }
    }

    public void sendToUsers(List<String> emails, String title,
                            String body, String channelId, String senderEmail) {
        emails.stream()
                .filter(email -> !email.equals(senderEmail))
                .forEach(email -> sendToUser(email, title, body, channelId));
    }

    private void sendNotification(String token, String title, String body, String channelId) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase is not initialized. Skipping push notification.");
            return;
        }

        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build()
                    )
                    .putData("channelId", channelId)
                    .putData("type", "NEW_MESSAGE")
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId("samuschat_messages")
                                    .setSound("default")
                                    .build()
                            )
                            .build()
                    )
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.debug("Notificacao enviada: {}", response);
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("Token FCM invalido, removendo: {}", token);
                userDeviceRepository.deleteByFcmToken(token);
                return;
            }

            log.error("Erro ao enviar notificacao FCM: {}", e.getMessage());
        }
    }
}
