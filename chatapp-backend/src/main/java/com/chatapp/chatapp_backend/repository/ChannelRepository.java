package com.chatapp.chatapp_backend.repository;

import com.chatapp.chatapp_backend.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    // Busca todos os canais de um servidor
    List<Channel> findByServerId(Long serverId);
}