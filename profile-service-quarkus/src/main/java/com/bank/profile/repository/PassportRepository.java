package com.bank.profile.repository;

import com.bank.profile.entity.Passport;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PassportRepository implements PanacheRepository<Passport> {
}
