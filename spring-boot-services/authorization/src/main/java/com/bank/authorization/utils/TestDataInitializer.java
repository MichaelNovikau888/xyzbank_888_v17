package com.bank.authorization.utils;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.bank.authorization.entity.User;
import com.bank.authorization.repository.UserRepository;

@Component
public class TestDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {

        if (userRepository.findByProfileId(1L).isEmpty()) {

            final User adminUser = new User();
            adminUser.setProfileId(1L);
            adminUser.setRole("ROLE_ADMIN");
            adminUser.setPassword(passwordEncoder.encode("admin123"));

            userRepository.save(adminUser);
            System.out.println("Test ADMIN user created.");
        }

        if (userRepository.findByProfileId(2L).isEmpty()) {

            final User userUser = new User();
            userUser.setProfileId(2L);
            userUser.setRole("ROLE_USER");
            userUser.setPassword(passwordEncoder.encode("user123"));

            userRepository.save(userUser);
            System.out.println("Test USER user created.");
        }
    }
}
