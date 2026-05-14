package com.chatapp.chatapp_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "server_members")
@Data
public class ServerMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Email do membro
    @Column(nullable = false)
    private String userEmail;

    // Cargo: ADMIN, MOD, USER
    @Column(nullable = false)
    private String role = "USER";

    @Column(name = "joined_at")
    private LocalDateTime joinedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;
}