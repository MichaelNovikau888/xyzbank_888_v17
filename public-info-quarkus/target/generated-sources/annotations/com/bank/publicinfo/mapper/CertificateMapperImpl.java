package com.bank.publicinfo.mapper;

import com.bank.publicinfo.dto.CertificateDto;
import com.bank.publicinfo.entity.BankDetails;
import com.bank.publicinfo.entity.Certificate;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.Arrays;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-23T12:08:00+0300",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.6 (Amazon.com Inc.)"
)
@Singleton
@Named
public class CertificateMapperImpl implements CertificateMapper {

    @Override
    public CertificateDto toDto(Certificate e) {
        if ( e == null ) {
            return null;
        }

        CertificateDto certificateDto = new CertificateDto();

        certificateDto.setBankDetailsId( eBankDetailsId( e ) );
        certificateDto.setId( e.getId() );
        byte[] photo = e.getPhoto();
        if ( photo != null ) {
            certificateDto.setPhoto( Arrays.copyOf( photo, photo.length ) );
        }

        return certificateDto;
    }

    @Override
    public Certificate toEntity(CertificateDto dto) {
        if ( dto == null ) {
            return null;
        }

        Certificate certificate = new Certificate();

        certificate.setId( dto.getId() );
        byte[] photo = dto.getPhoto();
        if ( photo != null ) {
            certificate.setPhoto( Arrays.copyOf( photo, photo.length ) );
        }

        return certificate;
    }

    @Override
    public void updateFromDto(CertificateDto dto, Certificate e) {
        if ( dto == null ) {
            return;
        }

        if ( dto.getId() != null ) {
            e.setId( dto.getId() );
        }
        byte[] photo = dto.getPhoto();
        if ( photo != null ) {
            e.setPhoto( Arrays.copyOf( photo, photo.length ) );
        }
    }

    private Long eBankDetailsId(Certificate certificate) {
        if ( certificate == null ) {
            return null;
        }
        BankDetails bankDetails = certificate.getBankDetails();
        if ( bankDetails == null ) {
            return null;
        }
        Long id = bankDetails.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }
}
