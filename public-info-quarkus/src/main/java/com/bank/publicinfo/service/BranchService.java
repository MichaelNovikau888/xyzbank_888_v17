package com.bank.publicinfo.service;

import com.bank.publicinfo.dto.BranchDto;
import com.bank.publicinfo.dto.PagedResponse;

public interface BranchService {
    BranchDto create(BranchDto dto);
    BranchDto update(Long id, BranchDto dto);
    void deleteById(Long id);
    PagedResponse<BranchDto> getAll(int page, int size);
    BranchDto getById(Long id);
}
