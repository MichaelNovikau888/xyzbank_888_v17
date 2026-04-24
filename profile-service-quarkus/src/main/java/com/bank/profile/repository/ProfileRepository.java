package com.bank.profile.repository;
import com.bank.profile.entity.Profile;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class ProfileRepository implements PanacheRepository<Profile> {
    public Optional<Profile> findByEmail(String email) { return find("email", email).firstResultOptional(); }
    public Optional<Profile> findByPhoneNumber(Long phone) { return find("phoneNumber", phone).firstResultOptional(); }
    /** Поиск по СНИЛС */
    public Optional<Profile> findBySnils(Long snils) { return find("snils", snils).firstResultOptional(); }
    /** Поиск по ИНН */
    public Optional<Profile> findByInn(Long inn) { return find("inn", inn).firstResultOptional(); }
}
