package com.chatapp.chatapp_backend.controller;

import com.chatapp.chatapp_backend.dto.*;
import com.chatapp.chatapp_backend.service.AppearanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class AppearanceController {
    private final AppearanceService appearance;
    @PutMapping("/users/me/appearance") public ApiResponse<UserResponse> profile(Authentication auth, @RequestBody AppearanceService.ProfileAppearance request) { return ApiResponse.success(appearance.profile(auth.getName(), request)); }
    @PostMapping("/users/me/media/{slot}") public ApiResponse<UserResponse> profileMedia(Authentication auth, @PathVariable String slot, @RequestParam MultipartFile file) throws IOException { return ApiResponse.success(appearance.profileMedia(auth.getName(), slot, file)); }
    @DeleteMapping("/users/me/media/{slot}") public ApiResponse<UserResponse> deleteProfileMedia(Authentication auth, @PathVariable String slot) throws IOException { return ApiResponse.success(appearance.profileMedia(auth.getName(), slot, null)); }
    @PutMapping("/servers/{id}/appearance") public ApiResponse<ServerResponse> server(Authentication auth, @PathVariable long id, @RequestBody AppearanceService.ServerAppearance request) { return ApiResponse.success(appearance.server(id, auth.getName(), request)); }
    @PostMapping("/servers/{id}/media/{slot}") public ApiResponse<ServerResponse> serverMedia(Authentication auth, @PathVariable long id, @PathVariable String slot, @RequestParam MultipartFile file) throws IOException { return ApiResponse.success(appearance.serverMedia(id, auth.getName(), slot, file)); }
    @DeleteMapping("/servers/{id}/media/{slot}") public ApiResponse<ServerResponse> deleteServerMedia(Authentication auth, @PathVariable long id, @PathVariable String slot) throws IOException { return ApiResponse.success(appearance.serverMedia(id, auth.getName(), slot, null)); }
}
