package com.chatapp.chatapp_backend.repository;
import com.chatapp.chatapp_backend.entity.Friendship;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface FriendshipRepository extends JpaRepository<Friendship,Long> {
    Optional<Friendship> findByLowUserIdAndHighUserId(Long low,Long high);
    @Query("select f from Friendship f where f.lowUserId=:id or f.highUserId=:id order by f.createdAt desc")
    List<Friendship> forUser(@Param("id") Long id);
}
