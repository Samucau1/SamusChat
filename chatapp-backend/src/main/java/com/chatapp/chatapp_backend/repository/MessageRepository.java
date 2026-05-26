package com.chatapp.chatapp_backend.repository;

import com.chatapp.chatapp_backend.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // Busca mensagens de um canal com paginação
    // Pageable permite controlar quantas mensagens retornar por vez
    Page<Message> findByChannelIdOrderByCreatedAtDesc(Long channelId, Pageable pageable);
}