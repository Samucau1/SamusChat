package com.chatapp.chatapp_backend.factory;

import com.chatapp.chatapp_backend.entity.Channel;
import com.chatapp.chatapp_backend.entity.Server;
import com.chatapp.chatapp_backend.entity.ServerMember;
import org.springframework.stereotype.Component;

@Component
public class ServerFactory {

    public Server createServer(String ownerEmail, String name, String description) {
        Server server = new Server();
        server.setOwnerEmail(ownerEmail);
        server.setName(name.trim());
        server.setDescription(description != null ? description.trim() : null);
        return server;
    }

    public Channel createDefaultChannel(Server server) {
        Channel channel = new Channel();
        channel.setName("geral");
        channel.setType("TEXT");
        channel.setServer(server);
        return channel;
    }

    public ServerMember createMember(String email, Server server, String role) {
        ServerMember member = new ServerMember();
        member.setUserEmail(email);
        member.setServer(server);
        member.setRole(role);
        return member;
    }
}
