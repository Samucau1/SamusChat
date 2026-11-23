package com.chatapp.chatapp_backend.controller;
import com.chatapp.chatapp_backend.dto.ApiResponse;
import com.chatapp.chatapp_backend.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/friends")
public class FriendController {
    private final FriendService friends;
    @GetMapping public ApiResponse<List<FriendService.Profile>> list(Authentication a){return ApiResponse.success(friends.list(a.getName()));}
    @GetMapping("/search") public ApiResponse<List<FriendService.Profile>> search(Authentication a,@RequestParam String q){return ApiResponse.success(friends.search(a.getName(),q));}
    @GetMapping("/profiles/{id}") public ApiResponse<FriendService.Profile> profile(Authentication a,@PathVariable String id){return ApiResponse.success(friends.profile(a.getName(),id));}
    @PostMapping("/{id}/request") public ApiResponse<FriendService.Profile> request(Authentication a,@PathVariable String id){return ApiResponse.success(friends.change(a.getName(),id,"request"));}
    @PostMapping("/{id}/accept") public ApiResponse<FriendService.Profile> accept(Authentication a,@PathVariable String id){return ApiResponse.success(friends.change(a.getName(),id,"accept"));}
    @DeleteMapping("/{id}") public ApiResponse<FriendService.Profile> remove(Authentication a,@PathVariable String id){return ApiResponse.success(friends.change(a.getName(),id,"remove"));}
}
