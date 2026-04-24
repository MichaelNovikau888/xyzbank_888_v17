package com.bank.publicinfo.service;

import com.bank.publicinfo.dto.LicenseDto;
import java.util.List;

public interface LicenseService {
    LicenseDto create(LicenseDto dto);
    LicenseDto update(LicenseDto dto);
    void deleteById(Long id);
    List<LicenseDto> getByBankDetails(Long bankDetailsId);
    LicenseDto getById(Long id);
}
