package com.chatapp.chatapp_backend.repository;

import com.chatapp.chatapp_backend.entity.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServerRepository extends JpaRepository<Server, Long> {
    boolean existsByInviteCode(String inviteCode);
    java.util.Optional<Server> findByInviteCode(String inviteCode);
    @org.springframework.data.jpa.repository.Query("select s.inviteCode from Server s where s.id = :id")
    String inviteCodeOf(@org.springframework.data.repository.query.Param("id") Long id);
    // Busca todos os servidores de um dono
    List<Server> findByOwnerEmail(String ownerEmail);
}