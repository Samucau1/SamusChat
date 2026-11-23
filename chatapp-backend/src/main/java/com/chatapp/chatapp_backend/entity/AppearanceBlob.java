package com.chatapp.chatapp_backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity @Table(name="appearance_blobs") @Data
public class AppearanceBlob {
    @Id @Column(length=40) private String filename;
    @Column(nullable=false, length=32) private String contentType;
    // Binary bytea, not PostgreSQL large-object OIDs. URLs and bytes survive redeploys together.
    @lombok.ToString.Exclude @Column(nullable=false, columnDefinition="bytea") private byte[] content;
}
