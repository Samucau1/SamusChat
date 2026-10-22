package com.chatapp.chatapp_backend.repository;

import com.chatapp.chatapp_backend.entity.DirectMessage;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {
    interface UnreadSummary { String getSenderEmail(); long getCount(); long getLastId(); }
    @Query("select m.senderEmail as senderEmail, count(m) as count, max(m.id) as lastId from DirectMessage m where m.recipientEmail=:me and m.readAt is null group by m.senderEmail")
    List<UnreadSummary> unread(@Param("me") String me);

    boolean existsByIdAndSenderEmailAndRecipientEmail(Long id, String senderEmail, String recipientEmail);

    @Modifying
    @Query("update DirectMessage m set m.readAt=CURRENT_TIMESTAMP where m.recipientEmail=:me and m.senderEmail=:contact and m.id<=:throughId and m.readAt is null")
    int markRead(@Param("me") String me, @Param("contact") String contact, @Param("throughId") long throughId);
    @Query("select m from DirectMessage m where (m.senderEmail=:me and m.recipientEmail=:contact) or (m.senderEmail=:contact and m.recipientEmail=:me) order by m.id desc")
    List<DirectMessage> conversation(@Param("me") String me, @Param("contact") String contact, Pageable page);
}
