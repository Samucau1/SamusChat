package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.MessageRequest;
import com.chatapp.chatapp_backend.entity.*;
import com.chatapp.chatapp_backend.factory.MessageFactory;
import com.chatapp.chatapp_backend.mapper.MessageMapper;
import com.chatapp.chatapp_backend.repository.*;
import com.chatapp.chatapp_backend.strategy.NotificationStrategy;
import com.chatapp.chatapp_backend.websocket.MessageCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    @Mock private MessageRepository messages;
    @Mock private ChannelRepository channels;
    @Mock private UserRepository users;
    @Mock private ServerMemberRepository members;
    @Mock private PermissionService permissions;
    @Mock private NotificationStrategy notifications;
    @Mock private ApplicationEventPublisher events;
    private MessageService service;
    private Channel channel;

    @BeforeEach
    void setup() {
        service = new MessageService(messages, channels, users, permissions, members,
                new MessageFactory(), new MessageMapper(), notifications, events);
        var server = new Server(); server.setId(10L);
        channel = new Channel(); channel.setId(1L); channel.setServer(server);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void emptyContentDoesNotPersistOrPublish(String content) {
        assertThatThrownBy(() -> service.sendMessage("user@test.com", 1L, request(content)))
                .hasMessageContaining("nao pode estar vazia");
        verifyNoInteractions(messages, channels, notifications, events);
    }

    @Test
    void contentOverTheLimitDoesNotPersist() {
        assertThatThrownBy(() -> service.sendMessage("user@test.com", 1L, request("a".repeat(2001))))
                .hasMessageContaining("muito longa");
        verifyNoInteractions(messages, channels, events);
    }

    @Test
    void missingChannelDoesNotPersist() {
        assertThatThrownBy(() -> service.sendMessage("user@test.com", 99L, request("oi")))
                .hasMessage("Canal nao encontrado");
        verifyNoInteractions(messages, notifications, events);
    }

    @Test
    void nonMemberCannotSend() {
        when(channels.findById(1L)).thenReturn(Optional.of(channel));
        doThrow(new RuntimeException("Sem permissao")).when(permissions).requireMember(10L, "user@test.com");
        assertThatThrownBy(() -> service.sendMessage("user@test.com", 1L, request("oi"))).hasMessage("Sem permissao");
        verifyNoInteractions(users, messages, notifications, events);
    }

    @Test
    void missingUserCannotPublish() {
        when(channels.findById(1L)).thenReturn(Optional.of(channel));
        assertThatThrownBy(() -> service.sendMessage("user@test.com", 1L, request("oi")))
                .hasMessage("Usuario nao encontrado");
        verifyNoInteractions(messages, notifications, events);
    }

    @Test
    void contentAtTheLimitIsSavedAndPublishedWithThePersistedId() {
        validSender();
        when(messages.save(any())).thenAnswer(call -> {
            Message message = call.getArgument(0); message.setId(42L); return message;
        });
        var response = service.sendMessage("user@test.com", 1L, request("a".repeat(2000)));
        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getContent()).hasSize(2000);
        assertThat(response.getType()).isEqualTo("CHAT");
        var event = ArgumentCaptor.forClass(MessageCreatedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().message()).isEqualTo(response);
        verify(notifications).notify(List.of("user@test.com"), "user", response.getContent(), "1", "user@test.com");
    }

    @Test
    void databaseFailureDoesNotNotifyOrPublish() {
        when(channels.findById(1L)).thenReturn(Optional.of(channel));
        var user = new User(); user.setEmail("user@test.com");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(messages.save(any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> service.sendMessage("user@test.com", 1L, request("oi")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(notifications, events);
    }

    @Test
    void attachmentWithoutTextKeepsItsMetadataInTheEvent() {
        validSender();
        var response = service.sendMessageWithAttachment("user@test.com", 1L, null, "/uploads/file.png", "IMAGE");
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getAttachmentUrl()).isEqualTo("/uploads/file.png");
        assertThat(response.getAttachmentType()).isEqualTo("IMAGE");
        verify(events).publishEvent(new MessageCreatedEvent(response));
        verify(notifications).notify(List.of("user@test.com"), "user", "Enviou um anexo", "1", "user@test.com");
    }

    @Test
    void attachmentRequiresContentOrUrl() {
        assertThatThrownBy(() -> service.sendMessageWithAttachment("user@test.com", 1L, null, null, "FILE"))
                .hasMessageContaining("conteudo ou anexo");
        verifyNoInteractions(messages, channels, events);
    }

    @Test
    void historyReversesAnImmutableRepositoryPageWithoutMutatingIt() {
        when(channels.findById(1L)).thenReturn(Optional.of(channel));
        Message older = message("user@test.com"); older.setId(1L);
        Message newer = message("user@test.com"); newer.setId(2L);
        var page = new PageImpl<>(List.of(newer, older));
        when(messages.findByChannelIdOrderByCreatedAtDesc(1L, PageRequest.of(0, 50))).thenReturn(page);
        assertThat(service.getMessages("user@test.com", 1L, 0, 50)).extracting("id").containsExactly(1L, 2L);
        assertThat(page.getContent()).containsExactly(newer, older);
        verify(permissions).requireMember(10L, "user@test.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"author", "moderator", "stranger"})
    void deleteRespectsAuthorAndModeratorPermissions(String role) {
        String email = role.equals("author") ? "author@test.com" : "other@test.com";
        Message message = message("author@test.com");
        when(messages.findById(1L)).thenReturn(Optional.of(message));
        when(permissions.isModOrAdmin(10L, email)).thenReturn(role.equals("moderator"));
        if (role.equals("stranger")) {
            assertThatThrownBy(() -> service.deleteMessage(email, 1L)).hasMessageContaining("nao tem permissao");
            verify(messages, never()).delete(any());
        } else {
            service.deleteMessage(email, 1L);
            verify(messages).delete(message);
        }
    }

    private MessageRequest request(String content) {
        var request = new MessageRequest(); request.setContent(content); return request;
    }

    private Message message(String email) {
        var message = new Message(); message.setSenderEmail(email); message.setChannel(channel); return message;
    }

    private void validSender() {
        when(channels.findById(1L)).thenReturn(Optional.of(channel));
        var user = new User(); user.setEmail("user@test.com"); user.setUsername("user");
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var member = new ServerMember(); member.setUserEmail(user.getEmail());
        when(members.findByServerId(10L)).thenReturn(List.of(member));
    }
}
