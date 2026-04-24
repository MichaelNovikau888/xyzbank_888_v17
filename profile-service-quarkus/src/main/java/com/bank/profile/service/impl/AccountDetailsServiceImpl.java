package com.bank.profile.service.impl;

import com.bank.profile.dto.AccountDetailsDto;
import com.bank.profile.entity.AccountDetails;
import com.bank.profile.mapper.AccountDetailsMapper;
import com.bank.profile.repository.AccountDetailsRepository;
import com.bank.profile.service.AccountDetailsService;
import com.bank.profile.service.ProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class AccountDetailsServiceImpl implements AccountDetailsService {

    @Inject AccountDetailsRepository accountDetailsRepository;
    @Inject AccountDetailsMapper accountDetailsMapper;
    @Inject ProfileService profileService;

    @Override
    public List<AccountDetailsDto> getAll() {
        return accountDetailsRepository.listAll().stream()
                .map(accountDetailsMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public AccountDetailsDto get(Long id) {
        return accountDetailsMapper.toDto(
                accountDetailsRepository.findByIdOptional(id)
                        .orElseThrow(() -> new EntityNotFoundException(AccountDetails.class.getSimpleName()))
        );
    }

    @Override
    @Transactional
    public AccountDetailsDto create(AccountDetailsDto dto) {
        if (dto.getId() != null && accountDetailsRepository.findByIdOptional(dto.getId()).isPresent()) {
            throw new EntityExistsException(AccountDetails.class.getSimpleName());
        }
        // Создаём пустой профиль, если profileId не задан
        if (dto.getProfileId() == null) {
            dto.setProfileId(profileService.createEmpty().getId());
        }
        return accountDetailsMapper.toDto(
                accountDetailsRepository.getEntityManager()
                        .merge(accountDetailsMapper.toEntity(dto))
        );
    }

    @Override
    @Transactional
    public AccountDetailsDto update(Long id, AccountDetailsDto dto) {
        if (id == null || accountDetailsRepository.findByIdOptional(id).isEmpty()) {
            throw new EntityNotFoundException(AccountDetails.class.getSimpleName());
        }
        return accountDetailsMapper.toDto(
                accountDetailsRepository.getEntityManager()
                        .merge(accountDetailsMapper.toEntity(dto))
        );
    }

    @Override
    @Transactional
    public void delete(Long id) {
        accountDetailsRepository.findByIdOptional(id).ifPresent(accountDetailsRepository::delete);
    }
}
