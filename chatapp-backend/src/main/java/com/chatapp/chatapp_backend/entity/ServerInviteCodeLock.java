package com.chatapp.chatapp_backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Single database row used to serialize code allocation across API instances. */
@Entity
@Table(name = "server_invite_code_lock")
public class ServerInviteCodeLock {
    @Id private Integer id;
}
