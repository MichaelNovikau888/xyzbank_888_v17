package com.bank.profile.service;

import com.bank.profile.dto.ProfileDto;

public interface ProfileService extends BasicCrudService<ProfileDto> {
    ProfileDto createEmpty();
}
