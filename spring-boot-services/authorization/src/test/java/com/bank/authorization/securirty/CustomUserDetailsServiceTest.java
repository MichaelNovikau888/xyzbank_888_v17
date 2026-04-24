package com.bank.authorization.securirty;

import com.bank.authorization.entity.User;
import com.bank.authorization.repository.UserRepository;
import com.bank.authorization.security.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private UserRepository userRepository;

    private User givenUserWithRole(String role) {
        User user = new User();
        user.setProfileId(12345L);
        user.setPassword("securePassword");
        user.setRole(role);
        return user;
    }

    @Test
    void loadUserByUsername_shouldReturnUserDetails_whenUserExists() {
        User expectedUser = givenUserWithRole("ROLE_USER");
        when(userRepository.findByProfileId(expectedUser.getProfileId()))
                .thenReturn(Optional.of(expectedUser));

        var userDetails = customUserDetailsService.loadUserByUsername(
                expectedUser.getProfileId().toString());

        verify(userRepository).findByProfileId(expectedUser.getProfileId());
        assertEquals(expectedUser.getProfileId().toString(), userDetails.getUsername());
    }

    @Test
    void loadUserByUsername_shouldContainCorrectPassword_whenUserExists() {
        User expectedUser = givenUserWithRole("ROLE_USER");
        when(userRepository.findByProfileId(expectedUser.getProfileId()))
                .thenReturn(Optional.of(expectedUser));

        var userDetails = customUserDetailsService.loadUserByUsername(
                expectedUser.getProfileId().toString());

        assertEquals(expectedUser.getPassword(), userDetails.getPassword());
    }

    @Test
    void loadUserByUsername_shouldContainCorrectAuthorities_whenUserExists() {
        String expectedRole = "ROLE_ADMIN";
        User expectedUser = givenUserWithRole(expectedRole);
        when(userRepository.findByProfileId(expectedUser.getProfileId()))
                .thenReturn(Optional.of(expectedUser));

        var userDetails = customUserDetailsService.loadUserByUsername(
                expectedUser.getProfileId().toString());

        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals(expectedRole)));
    }

    @Test
    void loadUserByUsername_shouldThrowException_whenUserNotFound() {
        Long nonExistentProfileId = 999L;
        when(userRepository.findByProfileId(nonExistentProfileId))
                .thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> {
            customUserDetailsService.loadUserByUsername(nonExistentProfileId.toString());
        });
    }
}
