package com.chatapp.chatapp_backend.controller;

import com.chatapp.chatapp_backend.dto.MemberResponse;
import com.chatapp.chatapp_backend.dto.UpdateRoleRequest;
import com.chatapp.chatapp_backend.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/servers/{serverId}/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public ResponseEntity<List<MemberResponse>> getMembers(
            Authentication auth,
            @PathVariable Long serverId) {
        return ResponseEntity.ok(memberService.getMembers(serverId, auth.getName()));
    }

    @PutMapping("/role")
    public ResponseEntity<MemberResponse> updateRole(
            Authentication auth,
            @PathVariable Long serverId,
            @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(memberService.updateRole(serverId, auth.getName(), request));
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<Void> removeMember(
            Authentication auth,
            @PathVariable Long serverId,
            @PathVariable String email) {
        memberService.removeMember(serverId, auth.getName(), email);
        return ResponseEntity.noContent().build();
    }
}
