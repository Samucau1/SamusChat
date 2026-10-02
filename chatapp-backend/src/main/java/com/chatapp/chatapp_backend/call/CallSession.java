package com.chatapp.chatapp_backend.call;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="call_sessions") @Getter @Setter
public class CallSession {
    @Id private String id;
    @Column(nullable=false) private String caller;
    @Column(nullable=false) private String callee;
    @Column(nullable=false) private String state;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant callerSeen;
    @Column(nullable=false) private Instant calleeSeen;
    @Column(columnDefinition="text") private String offer;
    @Column(columnDefinition="text") private String answer;
    public boolean terminal() { return state.equals("ENDED") || state.equals("DECLINED") || state.equals("EXPIRED"); }
}
