package com.chatapp.chatapp_backend.repository;

import com.chatapp.chatapp_backend.entity.ServerMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ServerMemberRepository extends JpaRepository<ServerMember, Long> {
    List<ServerMember> findByServerId(Long serverId);
    List<ServerMember> findByUserEmail(String userEmail);
    Optional<ServerMember> findByServerIdAndUserEmail(Long serverId, String userEmail);
    boolean existsByServerIdAndUserEmail(Long serverId, String userEmail);
}
