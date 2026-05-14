package com.chatapp.chatapp_backend.repository;

import com.chatapp.chatapp_backend.entity.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServerRepository extends JpaRepository<Server, Long> {
    // Busca todos os servidores de um dono
    List<Server> findByOwnerEmail(String ownerEmail);
}