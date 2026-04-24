package com.bank.authorization.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.bank.authorization.entity.User;
import com.bank.authorization.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String profileId) throws UsernameNotFoundException {
        log.debug("Loading user by profileId: {}", profileId);

        final Long profileIdLong = Long.parseLong(profileId);

        final User user = userRepository.findByProfileId(profileIdLong)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with profileId: " + profileId));

        final List<GrantedAuthority> authorities = Collections
                .singletonList(new SimpleGrantedAuthority(user.getRole()));

        return new org.springframework.security.core.userdetails.User(
                user.getProfileId().toString(),
                user.getPassword(),
                true,
                true,
                true,
                true,
                authorities);
    }
}
