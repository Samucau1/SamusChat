package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.entity.Channel;
import com.chatapp.chatapp_backend.entity.Message;
import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.mapper.MessageMapper;
import com.chatapp.chatapp_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageMapperTests {
    private final UserRepository users = mock(UserRepository.class);
    private final MessageMapper mapper = new MessageMapper(users);

    @Test
    void historyLoadsAuthorsOnceAndPreservesOrderAndMissingAvatars() {
        User author = new User();
        author.setEmail("author@example.com");
        author.setAvatarUrl("/avatar.png");
        User withoutAvatar = new User();
        withoutAvatar.setEmail("plain@example.com");
        when(users.findByEmailIn(List.of(author.getEmail(), withoutAvatar.getEmail(), "removed@example.com")))
                .thenReturn(List.of(author, withoutAvatar));

        var result = mapper.toResponses(List.of(message(1L, author.getEmail()),
                message(2L, withoutAvatar.getEmail()), message(3L, author.getEmail()),
                message(4L, "removed@example.com")));

        assertEquals(List.of(1L, 2L, 3L, 4L), result.stream().map(r -> r.getId()).toList());
        assertEquals("/avatar.png", result.get(0).getSenderAvatarUrl());
        assertNull(result.get(1).getSenderAvatarUrl());
        assertEquals("/avatar.png", result.get(2).getSenderAvatarUrl());
        assertNull(result.get(3).getSenderAvatarUrl());
        verify(users).findByEmailIn(List.of(author.getEmail(), withoutAvatar.getEmail(), "removed@example.com"));
        verifyNoMoreInteractions(users);
    }

    @Test
    void emptyHistoryDoesNotQueryUsers() {
        assertTrue(mapper.toResponses(List.of()).isEmpty());
        verifyNoInteractions(users);
    }

    private Message message(Long id, String email) {
        Channel channel = new Channel();
        channel.setId(9L);
        Message message = new Message();
        message.setId(id);
        message.setSenderEmail(email);
        message.setChannel(channel);
        return message;
    }
}
