package com.bank.profile.repository;

import com.bank.profile.entity.ActualRegistration;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ActualRegistrationRepository implements PanacheRepository<ActualRegistration> {
}
