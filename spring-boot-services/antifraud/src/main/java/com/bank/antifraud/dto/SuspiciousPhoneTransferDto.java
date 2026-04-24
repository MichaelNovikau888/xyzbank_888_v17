package com.bank.antifraud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class SuspiciousPhoneTransferDto {
    long id;

    long phoneTransferId;

    boolean blocked;

    boolean suspicious;

    String blockedReason;

    String suspiciousReason;
}
