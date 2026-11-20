package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.repository.UserDeviceRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
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
    private final com.chatapp.chatapp_backend.security.PublicIdentityService identities;

    public void sendToUser(String userEmail, String title, String body, String channelId) {
        sendToUser(userEmail, title, body, channelId, null);
    }

    private void sendToUser(String userEmail, String title, String body, String channelId, String senderEmail) {
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
            sendNotification(token, channelId, identities.publicId(userEmail), identities.publicId(senderEmail));
        }
    }

    public void sendToUsers(List<String> emails, String title,
                            String body, String channelId, String senderEmail) {
        emails.stream()
                .filter(email -> !email.equals(senderEmail))
                .distinct()
                .forEach(email -> sendToUser(email, title, body, channelId, senderEmail));
    }

    private void sendNotification(String token, String channelId, String recipientId, String contactId) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase is not initialized. Skipping push notification.");
            return;
        }

        try {
            Message message = notificationMessage(token, channelId, recipientId, contactId);

            String response = FirebaseMessaging.getInstance().send(message);
            log.debug("Notificacao enviada: {}", response);
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                log.warn("Removendo registro de notificacoes expirado");
                userDeviceRepository.deleteByFcmToken(token);
                return;
            }

            log.error("Erro ao enviar notificacao FCM: {}", e.getMessage());
        }
    }

    static Message notificationMessage(String token, String channelId, String recipientId, String contactId) {
        var message = Message.builder().setToken(token)
                .putData("type", "NEW_MESSAGE")
                .putData("title", "SamusChat")
                .putData("body", "Você tem uma nova mensagem.")
                .putData("channelId", channelId)
                .putData("recipientId", recipientId)
                .setWebpushConfig(com.google.firebase.messaging.WebpushConfig.builder().putHeader("TTL", "300").build())
                .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(java.time.Duration.ofMinutes(5).toMillis()).build());
        if ("direct".equals(channelId) && contactId != null && contactId.startsWith("usr_")) {
            message.putData("contactId", contactId);
        }
        return message.build();
    }
}
