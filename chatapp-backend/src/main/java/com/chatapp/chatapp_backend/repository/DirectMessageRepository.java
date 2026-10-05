package com.chatapp.chatapp_backend.repository;

import com.chatapp.chatapp_backend.entity.DirectMessage;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {
    @Query("select m from DirectMessage m where (m.senderEmail=:me and m.recipientEmail=:contact) or (m.senderEmail=:contact and m.recipientEmail=:me) order by m.id desc")
    List<DirectMessage> conversation(@Param("me") String me, @Param("contact") String contact, Pageable page);
}
