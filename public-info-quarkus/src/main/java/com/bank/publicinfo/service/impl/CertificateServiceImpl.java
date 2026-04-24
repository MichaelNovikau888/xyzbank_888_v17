package com.bank.publicinfo.service.impl;

import com.bank.publicinfo.dto.CertificateDto;
import com.bank.publicinfo.entity.BankDetails;
import com.bank.publicinfo.entity.Certificate;
import com.bank.publicinfo.exception.EntityNotFoundException;
import com.bank.publicinfo.mapper.CertificateMapper;
import com.bank.publicinfo.repository.BankDetailsRepository;
import com.bank.publicinfo.repository.CertificateRepository;
import com.bank.publicinfo.interceptor.Auditable;
import com.bank.publicinfo.producer.CertificateProducer;
import com.bank.publicinfo.service.CertificateService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import java.util.List;

@ApplicationScoped
public class CertificateServiceImpl implements CertificateService {

    private static final Logger LOG = Logger.getLogger(CertificateServiceImpl.class);

    @Inject CertificateMapper   mapper;
    @Inject CertificateProducer certificateProducer;
    @Inject CertificateRepository repository;
    @Inject BankDetailsRepository bankRepo;

    private BankDetails requireBank(Long id) {
        return bankRepo.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("BankDetails not found: " + id));
    }

    @Override @Transactional
    @Auditable
    public CertificateDto create(CertificateDto dto) {
        if (dto == null) throw new IllegalArgumentException("Certificate DTO must not be null");
        Certificate entity = mapper.toEntity(dto);
        entity.setBankDetails(requireBank(dto.getBankDetailsId()));
        repository.persist(entity);
        certificateProducer.sendCreated(mapper.toDto(entity));
        LOG.infof("Certificate created: id=%d", entity.getId());
        return mapper.toDto(entity);
    }

    @Override @Transactional
    @Auditable(operation = "UPDATE")
    public CertificateDto update(CertificateDto dto) {
        if (dto == null || dto.getId() == null)
            throw new IllegalArgumentException("Certificate DTO and ID must not be null");
        Certificate existing = repository.findByIdOptional(dto.getId())
                .orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + dto.getId()));
        mapper.updateFromDto(dto, existing);
        existing.setBankDetails(requireBank(dto.getBankDetailsId()));
        repository.persist(existing);
        certificateProducer.sendUpdated(mapper.toDto(existing));
        return mapper.toDto(existing);
    }

    @Override @Transactional
    public void deleteById(Long id) {
        if (id == null) throw new IllegalArgumentException("Certificate ID must not be null");
        Certificate existing = repository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + id));
        certificateProducer.sendDeleted(id);
        repository.delete(existing);
    }

    @Override
    public List<CertificateDto> getByBankDetails(Long bankDetailsId) {
        BankDetails bank = requireBank(bankDetailsId);
        return repository.findByBankDetails(bank).stream().map(mapper::toDto).toList();
    }

    @Override
    public CertificateDto getById(Long id) {
        if (id == null) throw new IllegalArgumentException("Certificate ID must not be null");
        return mapper.toDto(repository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("Certificate not found: " + id)));
    }
}
