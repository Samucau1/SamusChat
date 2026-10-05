package com.chatapp.chatapp_backend.repository;
import com.chatapp.chatapp_backend.entity.PasswordRecovery;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PasswordRecoveryRepository extends JpaRepository<PasswordRecovery,String> {}
