package com.bank.publicinfo.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "certificate", schema = "public_info")
public class Certificate extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "photo", nullable = false)
    private byte[] photo;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "bank_details_id", nullable = false)
    private BankDetails bankDetails;

    public static List<Certificate> findByBankDetailsId(Long bankDetailsId) {
        return list("bankDetails.id", bankDetailsId);
    }
}
