package com.bank.publicinfo.service.impl;

import com.bank.publicinfo.dto.BankDetailsDto;
import com.bank.publicinfo.dto.PagedResponse;
import com.bank.publicinfo.entity.BankDetails;
import com.bank.publicinfo.exception.EntityNotFoundException;
import com.bank.publicinfo.exception.ValidationException;
import com.bank.publicinfo.mapper.BankDetailsMapper;
import com.bank.publicinfo.repository.BankDetailsRepository;
import com.bank.publicinfo.repository.CertificateRepository;
import com.bank.publicinfo.repository.LicenseRepository;
import com.bank.publicinfo.interceptor.Auditable;
import com.bank.publicinfo.producer.BankDetailsProducer;
import com.bank.publicinfo.service.BankDetailsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import java.util.List;

@ApplicationScoped
public class BankDetailsServiceImpl implements BankDetailsService {

    private static final Logger LOG = Logger.getLogger(BankDetailsServiceImpl.class);

    @Inject BankDetailsMapper   mapper;
    @Inject BankDetailsProducer bankDetailsProducer;
    @Inject BankDetailsRepository repository;
    @Inject LicenseRepository   licenseRepo;
    @Inject CertificateRepository certRepo;

    @Override @Transactional
    @Auditable
    public BankDetailsDto create(BankDetailsDto dto) {
        if (dto == null) throw new IllegalArgumentException("BankDetails DTO must not be null");
        // Проверка уникальности BIK — задействует BankDetailsRepository.findByBik()
        if (dto.getBik() != null && repository.findByBik(dto.getBik()).isPresent()) {
            throw new ValidationException(
                "BankDetails with BIK " + dto.getBik() + " already exists");
        }
        BankDetails entity = mapper.toEntity(dto);
        repository.persist(entity);
        bankDetailsProducer.sendCreated(mapper.toDto(entity));
        LOG.infof("BankDetails created: id=%d, bik=%d", entity.getId(), entity.getBik());
        return mapper.toDto(entity);
    }

    @Override @Transactional
    @Auditable(operation = "UPDATE")
    public BankDetailsDto update(BankDetailsDto dto) {
        if (dto == null || dto.getId() == null)
            throw new ValidationException("BankDetails DTO and ID must not be null");
        BankDetails existing = repository.findByIdOptional(dto.getId())
                .orElseThrow(() -> new EntityNotFoundException("BankDetails not found: " + dto.getId()));
        mapper.updateFromDto(dto, existing);
        repository.persist(existing);
        bankDetailsProducer.sendUpdated(mapper.toDto(existing));
        LOG.infof("BankDetails updated: id=%d", dto.getId());
        return mapper.toDto(existing);
    }

    @Override @Transactional
    public void deleteById(Long id) {
        if (id == null) throw new IllegalArgumentException("BankDetails ID must not be null");
        BankDetails existing = repository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("BankDetails not found: " + id));
        // Удаляем связанные лицензии и сертификаты перед удалением банка
        licenseRepo.deleteByBankDetailsId(id);
        certRepo.deleteByBankDetailsId(id);
        repository.delete(existing);
        bankDetailsProducer.sendDeleted(id);
        LOG.infof("BankDetails deleted: id=%d", id);
    }

    @Override
    public PagedResponse<BankDetailsDto> getAll(int page, int size) {
        List<BankDetailsDto> content = repository.findAll()
                .page(page, size).list()
                .stream().map(mapper::toDto).toList();
        return new PagedResponse<>(content, page, size, repository.count());
    }

    @Override
    public BankDetailsDto getById(Long id) {
        if (id == null) throw new IllegalArgumentException("BankDetails ID must not be null");
        return mapper.toDto(repository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("BankDetails not found: " + id)));
    }
}
