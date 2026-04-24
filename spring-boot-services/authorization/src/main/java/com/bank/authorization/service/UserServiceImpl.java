package com.bank.authorization.service;

import com.bank.authorization.dto.UserDto;
import com.bank.authorization.entity.User;
import com.bank.authorization.mapper.UserMapper;
import com.bank.authorization.outbox.AuthOutboxHelper;
import com.bank.authorization.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * UserServiceImpl с Transactional Outbox.
 */
 /**
 * Каждая мутация (save / update / delete) записывает событие в outbox_events
 * в той же транзакции. OutboxRelayScheduler асинхронно публикует их в Kafka.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String USER_NOT_FOUND = "User not found with id: ";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthOutboxHelper outboxHelper;

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {
        return userRepository.findById(id)
                .map(userMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException(USER_NOT_FOUND + id));
    }

    @Override
    @Transactional
    public UserDto save(UserDto userDto) {
        encodePasswordIfPresent(userDto);
        final User saved = userRepository.save(userMapper.toEntity(userDto));
        final UserDto savedDto = userMapper.toDto(saved);

        outboxHelper.enqueue(
                "auth.user.events",
                String.valueOf(saved.getId()),
                "UserCreated",
                savedDto
        );

        log.info("User saved: id={}", saved.getId());
        return savedDto;
    }

    @Override
    @Transactional
    public UserDto updateUser(Long id, UserDto userDto) {
        final User existing = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(USER_NOT_FOUND + id));

        encodePasswordIfPresent(userDto);
        userMapper.updateEntityFromDto(userDto, existing);
        final User updated = userRepository.save(existing);
        final UserDto updatedDto = userMapper.toDto(updated);

        outboxHelper.enqueue(
                "auth.user.events",
                String.valueOf(id),
                "UserUpdated",
                updatedDto
        );

        log.info("User updated: id={}", id);
        return updatedDto;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        if (!userRepository.existsById(id)) {
            throw new EntityNotFoundException(USER_NOT_FOUND + id);
        }
        userRepository.deleteById(id);

        outboxHelper.enqueue(
                "auth.user.events",
                String.valueOf(id),
                "UserDeleted",
                java.util.Map.of("id", id)
        );

        log.info("User deleted: id={}", id);
    }

    private void encodePasswordIfPresent(UserDto userDto) {
        if (StringUtils.hasText(userDto.getPassword())) {
            userDto.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }
    }
}
