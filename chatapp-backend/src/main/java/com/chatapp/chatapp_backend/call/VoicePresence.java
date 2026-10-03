package com.chatapp.chatapp_backend.call;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="voice_presences") @Getter @Setter
public class VoicePresence {
    @Id private String email;
    @Column(nullable=false) private Long channelId;
    @Column(nullable=false) private Instant seenAt;
}
