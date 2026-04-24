package com.bank.publicinfo.service.impl;

import com.bank.publicinfo.dto.ATMDto;
import com.bank.publicinfo.entity.ATM;
import com.bank.publicinfo.entity.Branch;
import com.bank.publicinfo.exception.EntityNotFoundException;
import com.bank.publicinfo.mapper.ATMMapper;
import com.bank.publicinfo.repository.ATMRepository;
import com.bank.publicinfo.repository.BranchRepository;
import com.bank.publicinfo.interceptor.Auditable;
import com.bank.publicinfo.producer.ATMProducer;
import com.bank.publicinfo.service.ATMService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import java.util.List;

@ApplicationScoped
public class ATMServiceImpl implements ATMService {

    private static final Logger LOG = Logger.getLogger(ATMServiceImpl.class);

    @Inject ATMMapper        mapper;
    @Inject ATMProducer atmProducer;
    @Inject ATMRepository    repository;
    @Inject BranchRepository branchRepo;

    private Branch requireBranch(Long id) {
        return branchRepo.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("Branch not found: " + id));
    }

    @Override @Transactional
    @Auditable
    public ATMDto create(ATMDto dto) {
        if (dto == null) throw new IllegalArgumentException("ATM DTO must not be null");
        ATM entity = mapper.toEntity(dto);
        entity.setBranch(requireBranch(dto.getBranchId()));
        repository.persist(entity);
        atmProducer.sendCreated(mapper.toDto(entity));
        LOG.infof("ATM created: id=%d, branchId=%d", entity.getId(), dto.getBranchId());
        return mapper.toDto(entity);
    }

    @Override @Transactional
    @Auditable(operation = "UPDATE")
    public ATMDto update(ATMDto dto) {
        if (dto == null || dto.getId() == null)
            throw new IllegalArgumentException("ATM DTO and ID must not be null");
        ATM existing = repository.findByIdOptional(dto.getId())
                .orElseThrow(() -> new EntityNotFoundException("ATM not found: " + dto.getId()));
        mapper.updateFromDto(dto, existing);
        existing.setBranch(requireBranch(dto.getBranchId()));
        repository.persist(existing);
        atmProducer.sendUpdated(mapper.toDto(existing));
        return mapper.toDto(existing);
    }

    @Override @Transactional
    public void deleteById(Long id) {
        if (id == null) throw new IllegalArgumentException("ATM ID must not be null");
        ATM existing = repository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("ATM not found: " + id));
        atmProducer.sendDeleted(id);
        repository.delete(existing);
    }

    @Override
    public List<ATMDto> getByBranch(Long branchId) {
        if (branchId == null) throw new IllegalArgumentException("Branch ID must not be null");
        return repository.findByBranchId(branchId).stream().map(mapper::toDto).toList();
    }

    @Override
    public ATMDto getById(Long id) {
        if (id == null) throw new IllegalArgumentException("ATM ID must not be null");
        return mapper.toDto(repository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("ATM not found: " + id)));
    }
}
