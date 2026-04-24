package com.bank.publicinfo.service;

import com.bank.publicinfo.dto.BankDetailsDto;
import com.bank.publicinfo.dto.PagedResponse;

public interface BankDetailsService {
    BankDetailsDto create(BankDetailsDto dto);
    BankDetailsDto update(BankDetailsDto dto);
    void deleteById(Long id);
    PagedResponse<BankDetailsDto> getAll(int page, int size);
    BankDetailsDto getById(Long id);
}
