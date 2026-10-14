package com.chatapp.chatapp_backend.call;

import com.chatapp.chatapp_backend.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/channels/{id}/call") @RequiredArgsConstructor
public class VoiceRoomController {
    private final VoiceRoomService rooms;
    @PostMapping("/join") public ApiResponse<VoiceRoomService.Room> join(@PathVariable Long id,Authentication a){return ApiResponse.success(rooms.join(id,a.getName()));}
    @GetMapping public ApiResponse<VoiceRoomService.Room> current(@PathVariable Long id,Authentication a){return ApiResponse.success(rooms.snapshot(id,a.getName()));}
    @PostMapping("/leave") public ApiResponse<String> leave(@PathVariable Long id,Authentication a){rooms.leave(id,a.getName());return ApiResponse.success("Voce saiu do canal");}
}
