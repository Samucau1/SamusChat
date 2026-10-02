package com.chatapp.chatapp_backend.repository;

import com.chatapp.chatapp_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.email=:email")
    Optional<User> lockedByEmail(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Query("select distinct u from User u, ServerMember a, ServerMember b where a.userEmail=:email and a.server.id=b.server.id and b.userEmail=u.email and u.email<>:email order by u.username")
    java.util.List<User> callContacts(@org.springframework.data.repository.query.Param("email") String email);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
