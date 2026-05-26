package com.chatapp.chatapp_backend.controller;

import com.chatapp.chatapp_backend.dto.MessageResponse;
import com.chatapp.chatapp_backend.service.FileStorageService;
import com.chatapp.chatapp_backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;
    private final MessageService messageService;

    @PostMapping("/channels/{channelId}/upload")
    public ResponseEntity<MessageResponse> uploadFile(
            Authentication auth,
            @PathVariable Long channelId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "content", required = false) String content) {

        try {
            String fileUrl = fileStorageService.saveFile(file);
            String type = file.getContentType() != null && file.getContentType().startsWith("image/")
                    ? "IMAGE"
                    : "FILE";

            MessageResponse response = messageService.sendMessageWithAttachment(
                    auth.getName(), channelId, content, fileUrl, type
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao fazer upload: " + e.getMessage());
        }
    }

    @DeleteMapping("/files")
    public ResponseEntity<Map<String, String>> deleteFile(@RequestParam String fileUrl) {
        fileStorageService.deleteFile(fileUrl);
        return ResponseEntity.ok(Map.of("message", "Arquivo deletado com sucesso"));
    }
}
