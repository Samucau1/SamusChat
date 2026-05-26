package com.chatapp.chatapp_backend.repository;

import com.chatapp.chatapp_backend.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    List<UserDevice> findByUserEmail(String userEmail);
    Optional<UserDevice> findByFcmToken(String fcmToken);
    boolean existsByFcmToken(String fcmToken);
    void deleteByFcmToken(String fcmToken);
}
