package com.chatapp.chatapp_backend.controller;
import com.chatapp.chatapp_backend.service.AppearanceMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@RestController @RequiredArgsConstructor
public class AppearanceFileController {
    private final AppearanceMediaService media;
    @GetMapping("/uploads/appearance/{filename}")
    public ResponseEntity<byte[]> image(@PathVariable String filename) throws IOException {
        var result=media.load(filename);
        if(result.isEmpty()) return ResponseEntity.notFound().build();
        var blob=result.get();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(blob.getContentType()))
            .cacheControl(CacheControl.maxAge(1,TimeUnit.DAYS).cachePublic().immutable())
            .header("X-Content-Type-Options","nosniff").body(blob.getContent());
    }
}
