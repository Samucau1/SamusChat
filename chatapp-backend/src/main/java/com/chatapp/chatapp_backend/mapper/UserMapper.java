package com.chatapp.chatapp_backend.mapper;

import com.chatapp.chatapp_backend.dto.UserResponse;
import com.chatapp.chatapp_backend.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        return UserResponse.builder()
                .id(user.getId())
                .avatarUrl(user.getAvatarUrl())
                .bannerUrl(user.getBannerUrl())
                .backgroundUrl(user.getBackgroundUrl())
                .accentColor(user.getAccentColor())
                .bio(user.getBio())
                .username(user.getUsername())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
