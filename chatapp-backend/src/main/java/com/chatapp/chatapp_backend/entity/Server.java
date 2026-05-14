package com.chatapp.chatapp_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "servers")
@Data
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    // Dono do servidor — referencia o email do usuário
    @Column(nullable = false)
    private String ownerEmail;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Um servidor tem muitos canais
    @OneToMany(mappedBy = "server", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Channel> channels;
}