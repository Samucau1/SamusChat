package com.chatapp.chatapp_backend.repository;

import com.chatapp.chatapp_backend.entity.AttachmentBlob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentBlobRepository extends JpaRepository<AttachmentBlob, String> {}
