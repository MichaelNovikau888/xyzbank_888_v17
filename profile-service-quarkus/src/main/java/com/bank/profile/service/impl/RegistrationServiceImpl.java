package com.bank.profile.service.impl;

import com.bank.profile.dto.RegistrationDto;
import com.bank.profile.entity.Registration;
import com.bank.profile.mapper.RegistrationMapper;
import com.bank.profile.repository.RegistrationRepository;
import com.bank.profile.service.RegistrationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class RegistrationServiceImpl implements RegistrationService {

    @Inject RegistrationRepository registrationRepository;
    @Inject RegistrationMapper registrationMapper;

    @Override
    public List<RegistrationDto> getAll() {
        return registrationRepository.listAll().stream()
                .map(registrationMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public RegistrationDto get(Long id) {
        return registrationMapper.toDto(
                registrationRepository.findByIdOptional(id)
                        .orElseThrow(() -> new EntityNotFoundException(Registration.class.getSimpleName()))
        );
    }

    @Override
    @Transactional
    public RegistrationDto create(RegistrationDto dto) {
        Registration entity = registrationMapper.toRegistration(dto);
        registrationRepository.persist(entity);
        return registrationMapper.toDto(entity);
    }

    @Override
    @Transactional
    public RegistrationDto update(Long id, RegistrationDto dto) {
        if (id == null || registrationRepository.findByIdOptional(id).isEmpty()) {
            throw new EntityNotFoundException(Registration.class.getSimpleName());
        }
        Registration entity = registrationMapper.toRegistration(dto);
        return registrationMapper.toDto(registrationRepository.getEntityManager().merge(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        registrationRepository.findByIdOptional(id).ifPresent(registrationRepository::delete);
    }
}
