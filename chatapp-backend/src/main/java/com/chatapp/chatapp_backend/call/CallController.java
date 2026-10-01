package com.chatapp.chatapp_backend.call;

import com.chatapp.chatapp_backend.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.List;

@RestController @RequestMapping("/api/calls") @RequiredArgsConstructor
public class CallController {
    private final CallService service;
    private final IceConfiguration ice;
    public record Invite(String callee,String requestId) {}
    public record Action(String action,String sdp) {}
    @GetMapping("/contacts") public ApiResponse<List<CallService.Contact>> contacts(Authentication a){return ApiResponse.success(service.contacts(a.getName()));}
    @GetMapping("/ice") public ApiResponse<List<IceConfiguration.Server>> ice(Authentication a){return ApiResponse.success(ice.servers(a.getName()));}
    @GetMapping public ApiResponse<List<CallService.View>> current(Authentication a){return ApiResponse.success(service.current(a.getName()));}
    @GetMapping("/{id}") public ApiResponse<CallService.View> get(@PathVariable String id,Authentication a){return ApiResponse.success(service.get(id,a.getName()));}
    @PostMapping public ApiResponse<CallService.View> invite(@RequestBody Invite r,Authentication a){return ApiResponse.success(service.invite(a.getName(),r.callee(),r.requestId()));}
    @PostMapping("/{id}") public ApiResponse<CallService.View> action(@PathVariable String id,@RequestBody Action r,Authentication a){return ApiResponse.success(service.action(id,a.getName(),r.action(),r.sdp()));}
}
