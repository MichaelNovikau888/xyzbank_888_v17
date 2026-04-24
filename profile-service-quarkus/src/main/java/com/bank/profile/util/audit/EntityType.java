package com.bank.profile.util.audit;

public enum EntityType {
    None("woops"),
    AccountDetailsDto("AccountDetails"),
    ProfileDto("Profile"),
    PassportDto("Passport"),
    RegistrationDto("Registration"),
    ActualRegistrationDto("ActualRegistration");

    private final String entity;

    EntityType(final String e) { entity = e; }

    @Override
    public String toString() { return entity; }
}
