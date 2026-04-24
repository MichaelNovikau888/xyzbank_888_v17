package com.bank.authorization.service;

import com.bank.authorization.dto.UserDto;
import java.util.List;

public interface UserService {
    List<UserDto> getAllUsers();
    UserDto getUserById(Long id);
    UserDto save(UserDto userDto);
    UserDto updateUser(Long id, UserDto userDto);
    void deleteById(Long id);
}
