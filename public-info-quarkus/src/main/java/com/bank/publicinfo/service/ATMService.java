package com.bank.publicinfo.service;

import com.bank.publicinfo.dto.ATMDto;
import java.util.List;

public interface ATMService {
    ATMDto create(ATMDto dto);
    ATMDto update(ATMDto dto);
    void deleteById(Long id);
    List<ATMDto> getByBranch(Long branchId);
    ATMDto getById(Long id);
}
