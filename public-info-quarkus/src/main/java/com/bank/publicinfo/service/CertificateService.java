package com.bank.publicinfo.service;

import com.bank.publicinfo.dto.CertificateDto;
import java.util.List;

public interface CertificateService {
    CertificateDto create(CertificateDto dto);
    CertificateDto update(CertificateDto dto);
    void deleteById(Long id);
    List<CertificateDto> getByBankDetails(Long bankDetailsId);
    CertificateDto getById(Long id);
}
