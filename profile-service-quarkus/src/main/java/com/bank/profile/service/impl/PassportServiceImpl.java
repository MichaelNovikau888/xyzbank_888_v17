package com.bank.profile.service.impl;

import com.bank.profile.dto.PassportDto;
import com.bank.profile.entity.Passport;
import com.bank.profile.entity.Registration;
import com.bank.profile.mapper.PassportMapper;
import com.bank.profile.repository.PassportRepository;
import com.bank.profile.repository.RegistrationRepository;
import com.bank.profile.service.PassportService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class PassportServiceImpl implements PassportService {

    @Inject PassportRepository passportRepository;
    @Inject PassportMapper passportMapper;
    @Inject RegistrationRepository registrationRepository;

    @Override
    public List<PassportDto> getAll() {
        return passportRepository.listAll().stream()
                .map(passportMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public PassportDto get(Long id) {
        return passportMapper.toDto(
                passportRepository.findByIdOptional(id)
                        .orElseThrow(() -> new EntityNotFoundException(Passport.class.getSimpleName()))
        );
    }

    @Override
    @Transactional
    public PassportDto create(PassportDto dto) {
        Passport passport = passportMapper.toEntity(dto);
        validateRegistration(passport);
        passportRepository.persist(passport);
        return passportMapper.toDto(passport);
    }

    @Override
    @Transactional
    public PassportDto update(Long id, PassportDto dto) {
        if (passportRepository.findByIdOptional(id).isEmpty()) {
            throw new EntityNotFoundException(Passport.class.getSimpleName());
        }
        Passport passport = passportMapper.toEntity(dto);
        validateRegistration(passport);
        return passportMapper.toDto(passportRepository.getEntityManager().merge(passport));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        passportRepository.findByIdOptional(id).ifPresent(passportRepository::delete);
    }

    private void validateRegistration(Passport passport) {
        if (passport.getRegistration() == null ||
                passport.getRegistration().getId() == null ||
                registrationRepository.findByIdOptional(passport.getRegistration().getId()).isEmpty()) {
            throw new EntityNotFoundException(Registration.class.getSimpleName());
        }
    }
}
