package com.bank.history.mapper;

import com.bank.history.dto.HistoryDto;
import com.bank.history.entity.History;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-23T12:19:39+0300",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.6 (Amazon.com Inc.)"
)
@Singleton
@Named
public class HistoryMapperImpl implements HistoryMapper {

    @Override
    public HistoryDto toDto(History history) {
        if ( history == null ) {
            return null;
        }

        HistoryDto historyDto = new HistoryDto();

        historyDto.setId( history.getId() );
        historyDto.setTransferAuditId( history.getTransferAuditId() );
        historyDto.setProfileAuditId( history.getProfileAuditId() );
        historyDto.setAccountAuditId( history.getAccountAuditId() );
        historyDto.setAntiFraudAuditId( history.getAntiFraudAuditId() );
        historyDto.setPublicBankInfoAuditId( history.getPublicBankInfoAuditId() );
        historyDto.setAuthorizationAuditId( history.getAuthorizationAuditId() );
        historyDto.setEventType( history.getEventType() );
        historyDto.setEventData( history.getEventData() );
        historyDto.setCreatedAt( history.getCreatedAt() );
        historyDto.setServiceName( history.getServiceName() );

        return historyDto;
    }

    @Override
    public History toEntity(HistoryDto historyDto) {
        if ( historyDto == null ) {
            return null;
        }

        History history = new History();

        history.setId( historyDto.getId() );
        history.setTransferAuditId( historyDto.getTransferAuditId() );
        history.setProfileAuditId( historyDto.getProfileAuditId() );
        history.setAccountAuditId( historyDto.getAccountAuditId() );
        history.setAntiFraudAuditId( historyDto.getAntiFraudAuditId() );
        history.setPublicBankInfoAuditId( historyDto.getPublicBankInfoAuditId() );
        history.setAuthorizationAuditId( historyDto.getAuthorizationAuditId() );
        history.setEventType( historyDto.getEventType() );
        history.setEventData( historyDto.getEventData() );
        history.setServiceName( historyDto.getServiceName() );
        history.setCreatedAt( historyDto.getCreatedAt() );

        return history;
    }
}
