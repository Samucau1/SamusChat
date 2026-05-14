package com.chatapp.chatapp_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "channels")
@Data
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Tipo do canal: TEXT ou VOICE
    @Column(nullable = false)
    private String type = "TEXT";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Muitos canais pertencem a um servidor
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;
}