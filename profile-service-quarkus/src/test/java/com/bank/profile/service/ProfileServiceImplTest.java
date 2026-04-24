package com.bank.profile.service;

import com.bank.profile.dto.ProfileDto;
import com.bank.profile.entity.Profile;
import com.bank.profile.exception.EntityNotUniqueException;
import com.bank.profile.mapper.ProfileMapper;
import com.bank.profile.repository.ProfileRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityNotFoundException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты ProfileServiceImpl.
 * CDI-контекст поднимается, репозиторий и маппер — моки.
 */
@QuarkusTest
class ProfileServiceImplTest {

    @InjectMock
    ProfileRepository profileRepository;

    @InjectMock
    ProfileMapper profileMapper;

    @Inject
    ProfileService profileService;

    private Profile buildProfile(Long id, String email, Long snils, Long inn) {
        Profile p = new Profile();
        p.setId(id);
        p.setEmail(email);
        p.setSnils(snils);
        p.setInn(inn);
        p.setPhoneNumber("79001234567");
        return p;
    }

    private ProfileDto buildDto(Long id, String email, Long snils, Long inn) {
        ProfileDto dto = new ProfileDto();
        dto.setId(id);
        dto.setEmail(email);
        dto.setSnils(snils);
        dto.setInn(inn);
        return dto;
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create: уникальные поля — persist вызывается, dto возвращается")
    void create_uniqueFields_callsPersist() {
        ProfileDto dto = buildDto(null, "new@example.com", 11100000001L, 111000000001L);
        Profile entity = buildProfile(1L, "new@example.com", 11100000001L, 111000000001L);
        ProfileDto expected = buildDto(1L, "new@example.com", 11100000001L, 111000000001L);

        when(profileRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(profileRepository.findByInn(111000000001L)).thenReturn(Optional.empty());
        when(profileRepository.findBySnils(11100000001L)).thenReturn(Optional.empty());
        when(profileMapper.toEntity(dto)).thenReturn(entity);
        when(profileMapper.toDto(entity)).thenReturn(expected);
        doNothing().when(profileRepository).persist(any(Profile.class));

        ProfileDto result = profileService.create(dto);

        verify(profileRepository).persist(entity);
        Assertions.assertThat(result.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    @DisplayName("create: дублирующий email — EntityNotUniqueException")
    void create_duplicateEmail_throwsEntityNotUnique() {
        ProfileDto dto = buildDto(null, "dup@example.com", 22200000002L, 222000000002L);
        when(profileRepository.findByEmail("dup@example.com"))
                .thenReturn(Optional.of(buildProfile(1L, "dup@example.com", 22200000002L, 222000000002L)));

        Assertions.assertThatThrownBy(() -> profileService.create(dto))
                .isInstanceOf(EntityNotUniqueException.class);

        verify(profileRepository, never()).persist(any(Profile.class));
    }

    @Test
    @DisplayName("create: дублирующий ИНН — EntityNotUniqueException")
    void create_duplicateInn_throwsEntityNotUnique() {
        ProfileDto dto = buildDto(null, "unique@example.com", 33300000003L, 333000000003L);
        when(profileRepository.findByEmail("unique@example.com")).thenReturn(Optional.empty());
        when(profileRepository.findByInn(333000000003L))
                .thenReturn(Optional.of(buildProfile(2L, "other@example.com", 33300000003L, 333000000003L)));

        Assertions.assertThatThrownBy(() -> profileService.create(dto))
                .isInstanceOf(EntityNotUniqueException.class);

        verify(profileRepository, never()).persist(any(Profile.class));
    }

    @Test
    @DisplayName("create: дублирующий СНИЛС — EntityNotUniqueException")
    void create_duplicateSnils_throwsEntityNotUnique() {
        ProfileDto dto = buildDto(null, "unique2@example.com", 44400000004L, 444000000004L);
        when(profileRepository.findByEmail("unique2@example.com")).thenReturn(Optional.empty());
        when(profileRepository.findByInn(444000000004L)).thenReturn(Optional.empty());
        when(profileRepository.findBySnils(44400000004L))
                .thenReturn(Optional.of(buildProfile(3L, "other2@example.com", 44400000004L, 444000000004L)));

        Assertions.assertThatThrownBy(() -> profileService.create(dto))
                .isInstanceOf(EntityNotUniqueException.class);

        verify(profileRepository, never()).persist(any(Profile.class));
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("get: существующий ID — возвращает dto")
    void get_exists_returnsDto() {
        Profile entity = buildProfile(10L, "found@example.com", 55500000005L, 555000000005L);
        ProfileDto expected = buildDto(10L, "found@example.com", 55500000005L, 555000000005L);

        when(profileRepository.findByIdOptional(10L)).thenReturn(Optional.of(entity));
        when(profileMapper.toDto(entity)).thenReturn(expected);

        ProfileDto result = profileService.get(10L);
        Assertions.assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("get: несуществующий ID — EntityNotFoundException")
    void get_notExists_throwsEntityNotFound() {
        when(profileRepository.findByIdOptional(999L)).thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> profileService.get(999L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAll: возвращает список всех профилей")
    void getAll_returnsList() {
        Profile p1 = buildProfile(1L, "a@example.com", 11100000001L, 111000000001L);
        Profile p2 = buildProfile(2L, "b@example.com", 22200000002L, 222000000002L);
        ProfileDto d1 = buildDto(1L, "a@example.com", 11100000001L, 111000000001L);
        ProfileDto d2 = buildDto(2L, "b@example.com", 22200000002L, 222000000002L);

        when(profileRepository.listAll()).thenReturn(List.of(p1, p2));
        when(profileMapper.toDto(p1)).thenReturn(d1);
        when(profileMapper.toDto(p2)).thenReturn(d2);

        List<ProfileDto> result = profileService.getAll();
        Assertions.assertThat(result).hasSize(2);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update: существующий ID — поля обновляются, возвращается dto")
    void update_exists_updatesFields() {
        Profile existing = buildProfile(5L, "old@example.com", 55500000005L, 555000000005L);
        ProfileDto updateDto = buildDto(5L, "new@example.com", 55500000005L, 555000000005L);
        updateDto.setNameOnCard("New Name");
        updateDto.setPhoneNumber("79001111111");
        ProfileDto expected = buildDto(5L, "new@example.com", 55500000005L, 555000000005L);

        when(profileRepository.findByIdOptional(5L)).thenReturn(Optional.of(existing));
        when(profileMapper.toDto(existing)).thenReturn(expected);
        doNothing().when(profileRepository).persist(any(Profile.class));

        ProfileDto result = profileService.update(5L, updateDto);

        verify(profileRepository).persist(existing);
        Assertions.assertThat(result.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    @DisplayName("update: несуществующий ID — EntityNotFoundException")
    void update_notExists_throwsEntityNotFound() {
        when(profileRepository.findByIdOptional(888L)).thenReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> profileService.update(888L, buildDto(888L, "x@x.com", 1L, 2L)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete: существующий ID — delete вызывается на репозитории")
    void delete_exists_callsDelete() {
        Profile entity = buildProfile(7L, "del@example.com", 77700000007L, 777000000007L);
        when(profileRepository.findByIdOptional(7L)).thenReturn(Optional.of(entity));

        profileService.delete(7L);

        verify(profileRepository).delete(entity);
    }

    @Test
    @DisplayName("delete: несуществующий ID — delete НЕ вызывается (идемпотентно)")
    void delete_notExists_noop() {
        when(profileRepository.findByIdOptional(999L)).thenReturn(Optional.empty());

        profileService.delete(999L);

        verify(profileRepository, never()).delete(any());
    }
}
