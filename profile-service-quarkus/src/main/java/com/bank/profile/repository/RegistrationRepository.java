package com.bank.profile.repository;

import com.bank.profile.entity.Registration;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RegistrationRepository implements PanacheRepository<Registration> {
}
