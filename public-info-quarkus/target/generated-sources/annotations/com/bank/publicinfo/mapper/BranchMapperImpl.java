package com.bank.publicinfo.mapper;

import com.bank.publicinfo.dto.BranchDto;
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
public class BranchMapperImpl implements BranchMapper {

    @Override
    public BranchDto toDto(Branch e) {
        if ( e == null ) {
            return null;
        }

        BranchDto branchDto = new BranchDto();

        branchDto.setId( e.getId() );
        branchDto.setAddress( e.getAddress() );
        branchDto.setPhoneNumber( e.getPhoneNumber() );
        branchDto.setCity( e.getCity() );
        branchDto.setStartOfWork( e.getStartOfWork() );
        branchDto.setEndOfWork( e.getEndOfWork() );

        return branchDto;
    }

    @Override
    public Branch toEntity(BranchDto dto) {
        if ( dto == null ) {
            return null;
        }

        Branch branch = new Branch();

        branch.setId( dto.getId() );
        branch.setAddress( dto.getAddress() );
        branch.setPhoneNumber( dto.getPhoneNumber() );
        branch.setCity( dto.getCity() );
        branch.setStartOfWork( dto.getStartOfWork() );
        branch.setEndOfWork( dto.getEndOfWork() );

        return branch;
    }

    @Override
    public void updateFromDto(BranchDto dto, Branch e) {
        if ( dto == null ) {
            return;
        }

        if ( dto.getId() != null ) {
            e.setId( dto.getId() );
        }
        if ( dto.getAddress() != null ) {
            e.setAddress( dto.getAddress() );
        }
        if ( dto.getPhoneNumber() != null ) {
            e.setPhoneNumber( dto.getPhoneNumber() );
        }
        if ( dto.getCity() != null ) {
            e.setCity( dto.getCity() );
        }
        if ( dto.getStartOfWork() != null ) {
            e.setStartOfWork( dto.getStartOfWork() );
        }
        if ( dto.getEndOfWork() != null ) {
            e.setEndOfWork( dto.getEndOfWork() );
        }
    }
}
