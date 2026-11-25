package com.chatapp.chatapp_backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "attachment_blobs")
@Data
public class AttachmentBlob {
    @Id @Column(length = 80) private String filename;
    @Column(nullable = false, length = 100) private String contentType;
    @lombok.ToString.Exclude
    @Column(nullable = false, columnDefinition = "bytea") private byte[] content;
}
