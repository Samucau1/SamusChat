package com.chatapp.chatapp_backend.controller;
import com.chatapp.chatapp_backend.dto.*;
import com.chatapp.chatapp_backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.FileSystemResource;
import java.io.IOException;

@RestController @RequiredArgsConstructor
public class FileController {
    private final AttachmentService attachments;
    private final FileStorageService storage;
    @PostMapping("/api/channels/{channelId}/upload")
    public ApiResponse<MessageResponse> upload(Authentication auth, @PathVariable Long channelId, @RequestParam("file") MultipartFile file, @RequestParam(value="content", required=false) String content) throws IOException {
        return ApiResponse.success(attachments.upload(auth.getName(), channelId, file, content), "Arquivo enviado com sucesso");
    }
    @GetMapping("/uploads/{filename}")
    public ResponseEntity<FileSystemResource> download(Authentication auth, @PathVariable String filename) {
        var file = attachments.download(auth.getName(), filename);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(storage.contentType(filename)))
            .header("Content-Disposition", ContentDisposition.attachment().filename(filename).build().toString())
            .header("Content-Security-Policy", "sandbox; default-src 'none'")
            .body(new FileSystemResource(file));
    }
    @DeleteMapping("/api/files")
    public ApiResponse<Void> delete(Authentication auth, @RequestParam String fileUrl) {
        attachments.delete(auth.getName(), fileUrl); return ApiResponse.success(null, "Arquivo deletado com sucesso");
    }
}
