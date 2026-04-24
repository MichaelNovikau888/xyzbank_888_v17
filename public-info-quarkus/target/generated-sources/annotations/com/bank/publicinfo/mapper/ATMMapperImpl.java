package com.bank.publicinfo.mapper;

import com.bank.publicinfo.dto.ATMDto;
import com.bank.publicinfo.entity.ATM;
import com.bank.publicinfo.entity.Branch;
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
public class ATMMapperImpl implements ATMMapper {

    @Override
    public ATMDto toDto(ATM e) {
        if ( e == null ) {
            return null;
        }

        ATMDto aTMDto = new ATMDto();

        aTMDto.setBranchId( eBranchId( e ) );
        aTMDto.setId( e.getId() );
        aTMDto.setAddress( e.getAddress() );
        aTMDto.setStartOfWork( e.getStartOfWork() );
        aTMDto.setEndOfWork( e.getEndOfWork() );
        aTMDto.setAllHours( e.getAllHours() );

        return aTMDto;
    }

    @Override
    public ATM toEntity(ATMDto dto) {
        if ( dto == null ) {
            return null;
        }

        ATM aTM = new ATM();

        aTM.setId( dto.getId() );
        aTM.setAddress( dto.getAddress() );
        aTM.setStartOfWork( dto.getStartOfWork() );
        aTM.setEndOfWork( dto.getEndOfWork() );
        aTM.setAllHours( dto.getAllHours() );

        return aTM;
    }

    @Override
    public void updateFromDto(ATMDto dto, ATM e) {
        if ( dto == null ) {
            return;
        }

        if ( dto.getId() != null ) {
            e.setId( dto.getId() );
        }
        if ( dto.getAddress() != null ) {
            e.setAddress( dto.getAddress() );
        }
        if ( dto.getStartOfWork() != null ) {
            e.setStartOfWork( dto.getStartOfWork() );
        }
        if ( dto.getEndOfWork() != null ) {
            e.setEndOfWork( dto.getEndOfWork() );
        }
        if ( dto.getAllHours() != null ) {
            e.setAllHours( dto.getAllHours() );
        }
    }

    private Long eBranchId(ATM aTM) {
        if ( aTM == null ) {
            return null;
        }
        Branch branch = aTM.getBranch();
        if ( branch == null ) {
            return null;
        }
        Long id = branch.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }
}
