package com.bank.publicinfo.enumtype;

public enum EntityType {
    BANK_DETAILS, BRANCH, ATM, LICENSE, CERTIFICATE;

    public static EntityType from(String className) {
        String s = className.toLowerCase();
        if (s.contains("bankdetails")) return BANK_DETAILS;
        if (s.contains("branch")) return BRANCH;
        if (s.contains("atm")) return ATM;
        if (s.contains("license")) return LICENSE;
        if (s.contains("certificate")) return CERTIFICATE;
        throw new IllegalArgumentException("Unknown entity type: " + className);
    }
}
