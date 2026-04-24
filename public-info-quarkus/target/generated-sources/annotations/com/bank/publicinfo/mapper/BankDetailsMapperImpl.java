package com.bank.publicinfo.mapper;

import com.bank.publicinfo.dto.BankDetailsDto;
import com.bank.publicinfo.entity.BankDetails;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-23T12:08:00+0300",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.6 (Amazon.com Inc.)"
)
@Singleton
@Named
public class BankDetailsMapperImpl implements BankDetailsMapper {

    @Override
    public BankDetailsDto toDto(BankDetails e) {
        if ( e == null ) {
            return null;
        }

        BankDetailsDto bankDetailsDto = new BankDetailsDto();

        bankDetailsDto.setId( e.getId() );
        bankDetailsDto.setBik( e.getBik() );
        bankDetailsDto.setInn( e.getInn() );
        bankDetailsDto.setKpp( e.getKpp() );
        bankDetailsDto.setCorAccount( e.getCorAccount() );
        bankDetailsDto.setCity( e.getCity() );
        bankDetailsDto.setJointStockCompany( e.getJointStockCompany() );
        bankDetailsDto.setName( e.getName() );

        return bankDetailsDto;
    }

    @Override
    public BankDetails toEntity(BankDetailsDto dto) {
        if ( dto == null ) {
            return null;
        }

        BankDetails bankDetails = new BankDetails();

        bankDetails.setId( dto.getId() );
        bankDetails.setBik( dto.getBik() );
        bankDetails.setInn( dto.getInn() );
        bankDetails.setKpp( dto.getKpp() );
        bankDetails.setCorAccount( dto.getCorAccount() );
        bankDetails.setCity( dto.getCity() );
        bankDetails.setJointStockCompany( dto.getJointStockCompany() );
        bankDetails.setName( dto.getName() );

        return bankDetails;
    }

    @Override
    public void updateFromDto(BankDetailsDto dto, BankDetails e) {
        if ( dto == null ) {
            return;
        }

        if ( dto.getId() != null ) {
            e.setId( dto.getId() );
        }
        if ( dto.getBik() != null ) {
            e.setBik( dto.getBik() );
        }
        if ( dto.getInn() != null ) {
            e.setInn( dto.getInn() );
        }
        if ( dto.getKpp() != null ) {
            e.setKpp( dto.getKpp() );
        }
        if ( dto.getCorAccount() != null ) {
            e.setCorAccount( dto.getCorAccount() );
        }
        if ( dto.getCity() != null ) {
            e.setCity( dto.getCity() );
        }
        if ( dto.getJointStockCompany() != null ) {
            e.setJointStockCompany( dto.getJointStockCompany() );
        }
        if ( dto.getName() != null ) {
            e.setName( dto.getName() );
        }
    }
}
