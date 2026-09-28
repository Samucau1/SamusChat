package com.chatapp.chatapp_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatapp.chatapp_backend.dto.WebSocketMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ChatWebSocketControllerTests {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void websocketFlowSavesAndBroadcastsMessageToChannelTopic(boolean nativeAndroid) throws Exception {
        String token = registerAndLogin();
        long channelId = createServerAndGetGeneralChannelId(token);

        WebSocketStompClient stompClient = new WebSocketStompClient(nativeAndroid
                ? new StandardWebSocketClient()
                : new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        MappingJackson2MessageConverter messageConverter = new MappingJackson2MessageConverter();
        messageConverter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(messageConverter);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        AtomicReference<Throwable> stompFailure = new AtomicReference<>();
        StompSession session = stompClient
                .connectAsync(nativeAndroid ? "ws://localhost:" + port + "/ws/websocket" : "http://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(
                            StompSession session,
                            StompCommand command,
                            StompHeaders headers,
                            byte[] payload,
                            Throwable exception) {
                        stompFailure.set(exception);
                    }

                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        stompFailure.set(exception);
                    }
                })
                .get(5, TimeUnit.SECONDS);

        CompletableFuture<WebSocketMessage> receivedMessage = new CompletableFuture<>();
        CompletableFuture<WebSocketMessage> restMessage = new CompletableFuture<>();
        session.subscribe("/topic/channel/" + channelId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return WebSocketMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                WebSocketMessage incoming = (WebSocketMessage) payload;
                if ("Enviada pelo Android via REST".equals(incoming.getContent())) restMessage.complete(incoming);
                else receivedMessage.complete(incoming);
            }
        });

        session.send("/app/channel/" + channelId, Map.of("content", "Mensagem em tempo real!"));

        WebSocketMessage message = receivedMessage.get(5, TimeUnit.SECONDS);

        assertThat(stompFailure.get()).isNull();
        assertThat(message.getId()).isEqualTo(1);
        assertThat(message.getContent()).isEqualTo("Mensagem em tempo real!");
        assertThat(message.getSenderEmail()).isEqualTo("samus@test.com");
        assertThat(message.getSenderUsername()).isEqualTo("samus");
        assertThat(message.getChannelId()).isEqualTo(channelId);
        assertThat(message.getType()).isEqualTo("CHAT");
        assertThat(message.getCreatedAt()).isNotNull();

        MvcResult result = mockMvc.perform(post("/api/channels/{id}/messages", channelId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Enviada pelo Android via REST\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn();
        WebSocketMessage broadcast = restMessage.get(5, TimeUnit.SECONDS);
        assertThat(broadcast.getId()).isEqualTo(objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong());
        assertThat(broadcast.getChannelId()).isEqualTo(channelId);

        session.disconnect();
        stompClient.stop();
    }

    private String registerAndLogin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "samus",
                                  "email": "samus@test.com",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "samus@test.com",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("data");
        return loginBody.get("token").asText();
    }

    private long createServerAndGetGeneralChannelId(String token) throws Exception {
        MvcResult createServerResult = mockMvc.perform(post("/api/servers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Servidor da Samus",
                                  "description": "Meu primeiro servidor"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channels[0].name").value("geral"))
                .andReturn();

        return objectMapper.readTree(createServerResult.getResponse().getContentAsString()).get("data")
                .get("channels")
                .get(0)
                .get("id")
                .asLong();
    }
}
