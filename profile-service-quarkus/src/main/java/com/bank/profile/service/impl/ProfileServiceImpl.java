package com.bank.profile.service.impl;

import com.bank.profile.interceptor.Auditable;
import com.bank.profile.dto.ProfileDto;
import com.bank.profile.entity.Profile;
import com.bank.profile.exception.EntityNotUniqueException;
import com.bank.profile.mapper.ProfileMapper;
import com.bank.profile.repository.ProfileRepository;
import com.bank.profile.service.ProfileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.stream.Collectors;

/**
 * В Spring: @Service + @RequiredArgsConstructor.
 * В Quarkus: @ApplicationScoped + @Inject.
 * @Transactional — jakarta.transaction, поведение идентично Spring.
 */
@ApplicationScoped
public class ProfileServiceImpl implements ProfileService {

    private static final Logger log = Logger.getLogger(ProfileServiceImpl.class);

    @Inject ProfileRepository profileRepository;
    @Inject ProfileMapper profileMapper;

    @Override
    @Transactional
    @Auditable
    public ProfileDto create(ProfileDto dto) {
        // Проверяем уникальность email
        if (dto.getEmail() != null) {
            profileRepository.findByEmail(dto.getEmail()).ifPresent(p -> {
                throw new EntityNotUniqueException(Profile.class.getSimpleName(), "email");
            });
        }
        // Проверяем уникальность ИНН
        if (dto.getInn() != null) {
            profileRepository.findByInn(dto.getInn()).ifPresent(p -> {
                throw new EntityNotUniqueException(Profile.class.getSimpleName(), "inn");
            });
        }
        // Проверяем уникальность СНИЛС
        if (dto.getSnils() != null) {
            profileRepository.findBySnils(dto.getSnils()).ifPresent(p -> {
                throw new EntityNotUniqueException(Profile.class.getSimpleName(), "snils");
            });
        }
        Profile profile = profileMapper.toEntity(dto);
        profileRepository.persist(profile);
        log.infof("Profile created: id=%d, email=%s", profile.getId(), profile.getEmail());
        return profileMapper.toDto(profile);
    }

    @Override
    @Transactional
    public ProfileDto createEmpty() {
        Profile profile = new Profile();
        profileRepository.persist(profile);
        log.infof("Empty profile created: id=%d", profile.getId());
        return profileMapper.toDto(profile);
    }

    @Override
    public ProfileDto get(Long id) {
        return profileMapper.toDto(
                profileRepository.findByIdOptional(id)
                        .orElseThrow(() -> new EntityNotFoundException(Profile.class.getSimpleName()))
        );
    }

    @Override
    public List<ProfileDto> getAll() {
        return profileRepository.listAll().stream()
                .map(profileMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @Auditable(operation = "UPDATE")
    public ProfileDto update(Long id, ProfileDto dto) {
        Profile existing = profileRepository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException(Profile.class.getSimpleName()));
        // Обновляем поля
        existing.setEmail(dto.getEmail());
        existing.setPhoneNumber(dto.getPhoneNumber());
        existing.setNameOnCard(dto.getNameOnCard());
        existing.setSnils(dto.getSnils());
        existing.setInn(dto.getInn());
        profileRepository.persist(existing);
        log.infof("Profile updated: id=%d", id);
        return profileMapper.toDto(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        profileRepository.findByIdOptional(id).ifPresent(profileRepository::delete);
        log.infof("Profile deleted: id=%d", id);
    }
}
