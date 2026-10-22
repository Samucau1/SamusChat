package com.chatapp.chatapp_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "direct_messages", indexes = @Index(name = "idx_direct_participants", columnList = "sender_email,recipient_email,id"))
@Data
public class DirectMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime readAt;
    @Column(name = "sender_email", nullable = false) private String senderEmail;
    @Column(name = "recipient_email", nullable = false) private String recipientEmail;
    @Column(nullable = false) private String senderUsername;
    @Transient private String senderAvatarUrl;
    @Column(nullable = false, length = 2000) private String content;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
}
