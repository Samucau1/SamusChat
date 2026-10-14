package com.chatapp.chatapp_backend.entity;
import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
@Entity @Table(name="password_recoveries") @Data
public class PasswordRecovery {
    @Id private String email;
    @Column(nullable=false) private String codeHash;
    @Column(nullable=false) private Instant expiresAt;
    @Column(nullable=false) private Instant sentAt;
    private int attempts;
    private String resetHash;
    private boolean consumed;
}
