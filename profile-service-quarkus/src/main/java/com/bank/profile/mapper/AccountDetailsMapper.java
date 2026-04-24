package com.bank.profile.mapper;

import com.bank.profile.dto.AccountDetailsDto;
import com.bank.profile.entity.AccountDetails;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * componentModel = JAKARTA — CDI-бин, инжектируется через @Inject.
 * Spring-аналог: componentModel = "spring".
 */
@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA)
public interface AccountDetailsMapper {

    @Mapping(source = "profile.id", target = "profileId")
    AccountDetailsDto toDto(AccountDetails entity);

    @Mapping(source = "profileId", target = "profile.id")
    AccountDetails toEntity(AccountDetailsDto dto);
}
