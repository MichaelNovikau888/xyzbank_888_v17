package com.bank.publicinfo.mapper;

import com.bank.publicinfo.dto.LicenseDto;
import com.bank.publicinfo.entity.BankDetails;
import com.bank.publicinfo.entity.License;
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
public class LicenseMapperImpl implements LicenseMapper {

    @Override
    public LicenseDto toDto(License e) {
        if ( e == null ) {
            return null;
        }

        LicenseDto licenseDto = new LicenseDto();

        licenseDto.setBankDetailsId( eBankDetailsId( e ) );
        licenseDto.setId( e.getId() );
        byte[] photo = e.getPhoto();
        if ( photo != null ) {
            licenseDto.setPhoto( Arrays.copyOf( photo, photo.length ) );
        }

        return licenseDto;
    }

    @Override
    public License toEntity(LicenseDto dto) {
        if ( dto == null ) {
            return null;
        }

        License license = new License();

        license.setId( dto.getId() );
        byte[] photo = dto.getPhoto();
        if ( photo != null ) {
            license.setPhoto( Arrays.copyOf( photo, photo.length ) );
        }

        return license;
    }

    @Override
    public void updateFromDto(LicenseDto dto, License e) {
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

    private Long eBankDetailsId(License license) {
        if ( license == null ) {
            return null;
        }
        BankDetails bankDetails = license.getBankDetails();
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
