package com.chatapp.chatapp_backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);
    private static final String SERVICE_ACCOUNT_PATH = "firebase-service-account.json";

    @PostConstruct
    public void initFirebase() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        ClassPathResource serviceAccount = new ClassPathResource(SERVICE_ACCOUNT_PATH);
        if (!serviceAccount.exists()) {
            log.warn("Firebase service account not found at classpath:{}. Push notifications are disabled.",
                    SERVICE_ACCOUNT_PATH);
            return;
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount.getInputStream()))
                .build();

        FirebaseApp.initializeApp(options);
        log.info("Firebase initialized successfully");
    }
}
