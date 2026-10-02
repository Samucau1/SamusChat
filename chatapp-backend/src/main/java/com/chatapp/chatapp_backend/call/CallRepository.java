package com.chatapp.chatapp_backend.call;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface CallRepository extends JpaRepository<CallSession,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CallSession c where c.id=:id")
    Optional<CallSession> locked(@Param("id") String id);
    @Query("select c from CallSession c where (c.caller=:email or c.callee=:email) and c.state in ('RINGING','CONNECTING','ACTIVE') order by c.createdAt desc")
    List<CallSession> active(@Param("email") String email);

    @Modifying
    @Query("update CallSession c set c.state='EXPIRED', c.offer=null, c.answer=null where c.state in ('RINGING','CONNECTING','ACTIVE') and ((c.state='RINGING' and c.createdAt<:ring) or (c.state='CONNECTING' and c.createdAt<:connecting) or (c.state<>'RINGING' and (c.callerSeen<:heartbeat or c.calleeSeen<:heartbeat)))")
    int expireAbandoned(@Param("ring") java.time.Instant ring, @Param("connecting") java.time.Instant connecting, @Param("heartbeat") java.time.Instant heartbeat);

    @Modifying
    @Query("delete from CallSession c where c.state in ('ENDED','DECLINED','EXPIRED') and c.createdAt<:before")
    int deleteOld(@Param("before") java.time.Instant before);
}
