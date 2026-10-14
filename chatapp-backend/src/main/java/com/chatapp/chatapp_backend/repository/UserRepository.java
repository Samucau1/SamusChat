package com.chatapp.chatapp_backend.repository;

import com.chatapp.chatapp_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.email=:email")
    Optional<User> lockedByEmail(@org.springframework.data.repository.query.Param("email") String email);

    java.util.List<User> findTop100ByEmailNotOrderByUsernameAsc(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleSubject(String subject);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
