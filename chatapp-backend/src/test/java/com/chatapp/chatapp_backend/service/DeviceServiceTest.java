package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.DeviceTokenRequest;
import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.repository.UserDeviceRepository;
import com.chatapp.chatapp_backend.repository.UserRepository;
import com.chatapp.chatapp_backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DeviceServiceTest {
    @Test void inFlightRegistrationRechecksRevocationAfterAcquiringTheLogoutLock() {
        var devices = mock(UserDeviceRepository.class);
        var users = mock(UserRepository.class);
        var jwt = mock(JwtUtil.class);
        when(users.lockedByEmail("user@test.com")).thenReturn(Optional.of(new User()));
        when(jwt.isTokenValid("revoked")).thenReturn(false);
        var service = new DeviceService(devices, users, jwt);
        assertThatThrownBy(() -> service.registerDevice("user@test.com", new DeviceTokenRequest(), "revoked"))
                .isInstanceOf(SecurityException.class);
        var order = inOrder(users, jwt);
        order.verify(users).lockedByEmail("user@test.com");
        order.verify(jwt).isTokenValid("revoked");
        verifyNoInteractions(devices);
    }
}
