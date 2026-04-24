package com.bank.profile.service.impl;

import com.bank.profile.dto.RegistrationDto;
import com.bank.profile.entity.ActualRegistration;
import com.bank.profile.mapper.RegistrationMapper;
import com.bank.profile.repository.ActualRegistrationRepository;
import com.bank.profile.service.ActualRegistrationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class ActualRegistrationServiceImpl implements ActualRegistrationService {

    @Inject ActualRegistrationRepository actualRegistrationRepository;
    @Inject RegistrationMapper registrationMapper;

    @Override
    public List<RegistrationDto> getAll() {
        return actualRegistrationRepository.listAll().stream()
                .map(registrationMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public RegistrationDto get(Long id) {
        return registrationMapper.toDto(
                actualRegistrationRepository.findByIdOptional(id)
                        .orElseThrow(() -> new EntityNotFoundException(ActualRegistration.class.getSimpleName()))
        );
    }

    @Override
    @Transactional
    public RegistrationDto create(RegistrationDto dto) {
        ActualRegistration entity = registrationMapper.toActualRegistration(dto);
        actualRegistrationRepository.persist(entity);
        return registrationMapper.toDto(entity);
    }

    @Override
    @Transactional
    public RegistrationDto update(Long id, RegistrationDto dto) {
        if (id == null || actualRegistrationRepository.findByIdOptional(id).isEmpty()) {
            throw new EntityNotFoundException(ActualRegistration.class.getSimpleName());
        }
        ActualRegistration entity = registrationMapper.toActualRegistration(dto);
        return registrationMapper.toDto(actualRegistrationRepository.getEntityManager().merge(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        actualRegistrationRepository.findByIdOptional(id).ifPresent(actualRegistrationRepository::delete);
    }
}
