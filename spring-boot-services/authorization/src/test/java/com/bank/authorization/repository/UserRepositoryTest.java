package com.bank.authorization.repository;

import com.bank.authorization.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.liquibase.enabled=false"
})
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void testFindByProfileId() {
        User user = givenUserWithRole("ROLE_USER");
        User savedUser = userRepository.save(user);

        Optional<User> foundUser = userRepository.findByProfileId(savedUser.getProfileId());

        assertTrue(foundUser.isPresent(), "User should be found by profileId");
        assertEquals(savedUser.getProfileId(), foundUser.get().getProfileId(), "Profile IDs should match");
        assertEquals("ROLE_USER", foundUser.get().getRole(), "Roles should match");
    }

    @Test
    void testFindByProfileId_NotFound() {
        Optional<User> foundUser = userRepository.findByProfileId(999L);

        assertFalse(foundUser.isPresent(), "User should not be found for non-existent profileId");
    }

    @Test
    void testFindByProfileId_WithDifferentRole() {
        User adminUser = givenUserWithRole("ROLE_ADMIN");
        User savedAdmin = userRepository.save(adminUser);

        Optional<User> foundUser = userRepository.findByProfileId(savedAdmin.getProfileId());

        assertTrue(foundUser.isPresent(), "Admin user should be found");
        assertEquals("ROLE_ADMIN", foundUser.get().getRole(), "Admin role should match");
    }

    private User givenUserWithRole(String role) {
        User user = new User();
        user.setProfileId((long) (Math.random() * 1000));
        user.setRole(role);
        user.setPassword("password");
        return user;
    }
}
