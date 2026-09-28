package com.chatapp.chatapp_backend.controller;

import com.chatapp.chatapp_backend.dto.ApiResponse;
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
    public ResponseEntity<ApiResponse<List<MemberResponse>>> getMembers(
            Authentication auth,
            @PathVariable Long serverId) {
        return ResponseEntity.ok(ApiResponse.success(memberService.getMembers(serverId, auth.getName())));
    }

    @PutMapping("/role")
    public ResponseEntity<ApiResponse<MemberResponse>> updateRole(
            Authentication auth,
            @PathVariable Long serverId,
            @RequestBody UpdateRoleRequest request) {
        MemberResponse response = memberService.updateRole(serverId, auth.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cargo atualizado com sucesso"));
    }

    @DeleteMapping("/{email}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            Authentication auth,
            @PathVariable Long serverId,
            @PathVariable String email) {
        memberService.removeMember(serverId, auth.getName(), email);
        return ResponseEntity.ok(ApiResponse.success(null, "Membro removido com sucesso"));
    }
}
