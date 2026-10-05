package com.chatapp.chatapp_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name="google_subject", unique=true)
    private String googleSubject;

    @Column(name="auth_version", nullable=false)
    @org.hibernate.annotations.ColumnDefault("0")
    private int authVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
