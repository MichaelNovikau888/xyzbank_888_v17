package com.bank.antifraud.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@Table("suspicious_card_transfer")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class SuspiciousCardTransfer {
    @Id
    private Long id;

    @Column("card_transfer_id")
    private long cardTransferId;

    @Column("is_blocked")
    private boolean blocked;

    @Column("is_suspicious")
    private boolean suspicious;

    @Column("blocked_reason")
    private String blockedReason;

    @Column("suspicious_reason")
    private String suspiciousReason;
}
