package com.chatapp.chatapp_backend.call;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VoicePresenceRepository extends JpaRepository<VoicePresence,String> {
    List<VoicePresence> findByChannelIdOrderByEmail(Long channelId);
}
