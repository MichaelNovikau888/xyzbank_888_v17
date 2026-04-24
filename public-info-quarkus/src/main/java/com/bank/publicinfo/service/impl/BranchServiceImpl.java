package com.bank.publicinfo.service.impl;

import com.bank.publicinfo.dto.BranchDto;
import com.bank.publicinfo.dto.PagedResponse;
import com.bank.publicinfo.entity.Branch;
import com.bank.publicinfo.exception.EntityNotFoundException;
import com.bank.publicinfo.exception.ValidationException;
import com.bank.publicinfo.mapper.BranchMapper;
import com.bank.publicinfo.repository.ATMRepository;
import com.bank.publicinfo.repository.BranchRepository;
import com.bank.publicinfo.interceptor.Auditable;
import com.bank.publicinfo.producer.BranchProducer;
import com.bank.publicinfo.service.BranchService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import java.util.List;

@ApplicationScoped
public class BranchServiceImpl implements BranchService {

    private static final Logger LOG = Logger.getLogger(BranchServiceImpl.class);

    @Inject BranchMapper    mapper;
    @Inject BranchProducer branchProducer;
    @Inject BranchRepository repository;
    @Inject ATMRepository   atmRepo;

    @Override @Transactional
    @Auditable
    public BranchDto create(BranchDto dto) {
        if (dto == null) throw new IllegalArgumentException("Branch DTO must not be null");
        Branch entity = mapper.toEntity(dto);
        repository.persist(entity);
        branchProducer.sendCreated(mapper.toDto(entity));
        LOG.infof("Branch created: id=%d", entity.getId());
        return mapper.toDto(entity);
    }

    @Override @Transactional
    @Auditable(operation = "UPDATE")
    public BranchDto update(Long id, BranchDto dto) {
        if (id == null || dto == null)
            throw new ValidationException("Branch ID and DTO must not be null");
        Branch existing = repository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("Branch not found: " + id));
        mapper.updateFromDto(dto, existing);
        repository.persist(existing);
        branchProducer.sendUpdated(mapper.toDto(existing));
        LOG.infof("Branch updated: id=%d", id);
        return mapper.toDto(existing);
    }

    @Override @Transactional
    public void deleteById(Long id) {
        if (id == null) throw new IllegalArgumentException("Branch ID must not be null");
        Branch existing = repository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("Branch not found: " + id));
        // Удаляем банкоматы отделения перед удалением самого отделения
        atmRepo.delete("branch.id", id);
        repository.delete(existing);
        branchProducer.sendDeleted(id);
        LOG.infof("Branch deleted: id=%d", id);
    }

    @Override
    public PagedResponse<BranchDto> getAll(int page, int size) {
        List<BranchDto> content = repository.findAll()
                .page(page, size).list()
                .stream().map(mapper::toDto).toList();
        return new PagedResponse<>(content, page, size, repository.count());
    }

    @Override
    public BranchDto getById(Long id) {
        if (id == null) throw new IllegalArgumentException("Branch ID must not be null");
        return mapper.toDto(repository.findByIdOptional(id)
                .orElseThrow(() -> new EntityNotFoundException("Branch not found: " + id)));
    }
}
