package com.chatapp.chatapp_backend.repository;

import com.chatapp.chatapp_backend.entity.ServerInviteCodeLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ServerInviteCodeLockRepository extends JpaRepository<ServerInviteCodeLock, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ServerInviteCodeLock l where l.id = 1")
    ServerInviteCodeLock lockAllocation();
}
