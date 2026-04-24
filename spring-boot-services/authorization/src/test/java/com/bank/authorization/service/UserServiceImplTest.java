package com.bank.authorization.service;

import com.bank.authorization.dto.UserDto;
import com.bank.authorization.entity.User;
import com.bank.authorization.mapper.UserMapper;
import com.bank.authorization.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @InjectMocks
    private UserServiceImpl userService;
    private User user;
    private UserDto userDto;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setProfileId(100L);
        user.setPassword("encodedPassword");

        userDto = new UserDto();
        userDto.setProfileId(100L);
        userDto.setPassword("rawPassword");
    }

    @Test
    void getAllUsers_ShouldReturnUserList() {
        when(userRepository.findAll()).thenReturn(Collections.singletonList(user));
        when(userMapper.toDto(any(User.class))).thenReturn(userDto);

        List<UserDto> users = userService.getAllUsers();

        assertFalse(users.isEmpty());
        assertEquals(1, users.size());
        verify(userRepository, times(1)).findAll();
    }

    @Test
    void getAllUsers_whenRepositoryThrowsException_shouldLogErrorAndRethrow() {
        doThrow(new RuntimeException("Test exception")).when(userRepository).findAll();

        assertThrows(Exception.class, () -> userService.getAllUsers());
    }

    @Test
    void getUserById_ShouldReturnUser_WhenUserExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toDto(any(User.class))).thenReturn(userDto);

        UserDto result = userService.getUserById(1L);

        assertNotNull(result);
        assertEquals(userDto.getProfileId(), result.getProfileId());
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    void getUserById_ShouldThrowException_WhenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> userService.getUserById(1L));
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    void save_ShouldEncryptPasswordAndSaveUser() {
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userMapper.toEntity(any(UserDto.class))).thenReturn(user);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toDto(any(User.class))).thenReturn(userDto);

        UserDto savedUser = userService.save(userDto);

        assertNotNull(savedUser);
        assertEquals(userDto.getProfileId(), savedUser.getProfileId());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void save_whenRepositoryThrowsException_shouldLogErrorAndRethrow() {
        UserDto userDto = new UserDto();
        doThrow(new RuntimeException("Test exception")).when(userRepository).save(any(User.class));

        assertThrows(Exception.class, () -> userService.save(userDto));
    }

    @Test
    void updateUser_ShouldUpdateExistingUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toDto(any(User.class))).thenReturn(userDto);

        UserDto updatedUser = userService.updateUser(1L, userDto);

        assertNotNull(updatedUser);
        assertEquals(userDto.getProfileId(), updatedUser.getProfileId());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void updateUser_whenRepositoryThrowsException_shouldLogErrorAndRethrow() {
        long id = 456L;
        UserDto userDto = new UserDto();
        when(userRepository.findById(eq(id))).thenReturn(Optional.of(new User()));
        doThrow(new RuntimeException("Test exception")).when(userRepository).save(any(User.class));

        assertThrows(Exception.class, () -> userService.updateUser(id, userDto));
    }

    @Test
    void deleteById_ShouldDeleteUser_WhenUserExists() {
        when(userRepository.existsById(1L)).thenReturn(true);
        doNothing().when(userRepository).deleteById(1L);

        assertDoesNotThrow(() -> userService.deleteById(1L));
        verify(userRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteById_ShouldThrowException_WhenUserNotFound() {
        when(userRepository.existsById(1L)).thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> userService.deleteById(1L));
        verify(userRepository, times(0)).deleteById(1L);
    }
}
