package com.bank.publicinfo.service.impl;

import com.bank.publicinfo.dto.LicenseDto;
import com.bank.publicinfo.entity.BankDetails;
import com.bank.publicinfo.entity.License;
import com.bank.publicinfo.exception.EntityNotFoundException;
import com.bank.publicinfo.mapper.LicenseMapper;
import com.bank.publicinfo.repository.BankDetailsRepository;
import com.bank.publicinfo.repository.LicenseRepository;
import com.bank.publicinfo.interceptor.Auditable;
import com.bank.publicinfo.producer.LicenseProducer;
import com.bank.publicinfo.service.LicenseService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import java.util.List;

@ApplicationScoped
public class LicenseServiceImpl implements LicenseService {

    private static final Logger LOG = Logger.getLogger(LicenseServiceImpl.class);

    @Inject LicenseMapper    mapper;
    @Inject LicenseProducer licenseProducer;
    @Inject LicenseRepository repository;
    @Inject BankDetailsRepository bankRepo;

    private BankDetails requireBank(Long id) {
        return bankRepo.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("BankDetails not found: " + id));
    }

    @Override @Transactional
    @Auditable
    public LicenseDto create(LicenseDto dto) {
        if (dto == null) throw new IllegalArgumentException("License DTO must not be null");
        License entity = mapper.toEntity(dto);
        entity.setBankDetails(requireBank(dto.getBankDetailsId()));
        repository.persist(entity);
        licenseProducer.sendCreated(mapper.toDto(entity));
        LOG.infof("License created: id=%d", entity.getId());
        return mapper.toDto(entity);
    }

    @Override @Transactional
    @Auditable(operation = "UPDATE")
    public LicenseDto update(LicenseDto dto) {
        if (dto == null || dto.getId() == null)
            throw new IllegalArgumentException("License DTO and ID must not be null");
        License existing = repository.findByIdOptional(dto.getId())
                .orElseThrow(() -> new EntityNotFoundException("License not found: " + dto.getId()));
        mapper.updateFromDto(dto, existing);
        existing.setBankDetails(requireBank(dto.getBankDetailsId()));
        repository.persist(existing);
        licenseProducer.sendUpdated(mapper.toDto(existing));
        return mapper.toDto(existing);
    }

    @Override @Transactional
    public void deleteById(Long id) {
        if (id == null) throw new IllegalArgumentException("License ID must not be null");
        License existing = repository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("License not found: " + id));
        licenseProducer.sendDeleted(id);
        repository.delete(existing);
    }

    @Override
    public List<LicenseDto> getByBankDetails(Long bankDetailsId) {
        BankDetails bank = requireBank(bankDetailsId);
        return repository.findByBankDetails(bank).stream().map(mapper::toDto).toList();
    }

    @Override
    public LicenseDto getById(Long id) {
        if (id == null) throw new IllegalArgumentException("License ID must not be null");
        return mapper.toDto(repository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("License not found: " + id)));
    }
}
