package com.chatapp.chatapp_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity @Table(name="friendships", uniqueConstraints=@UniqueConstraint(columnNames={"low_user_id","high_user_id"})) @Data
public class Friendship {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="low_user_id", nullable=false) private Long lowUserId;
    @Column(name="high_user_id", nullable=false) private Long highUserId;
    @Column(name="requested_by", nullable=false) private Long requestedBy;
    @Column(nullable=false) private boolean accepted;
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
}
