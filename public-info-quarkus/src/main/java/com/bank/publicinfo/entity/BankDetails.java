package com.bank.publicinfo.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "bank_details", schema = "public_info",
        uniqueConstraints = {
                @UniqueConstraint(name = "bank_details_bik_key", columnNames = {"bik"}),
                @UniqueConstraint(name = "bank_details_inn_key", columnNames = {"inn"}),
                @UniqueConstraint(name = "bank_details_kpp_key", columnNames = {"kpp"}),
                @UniqueConstraint(name = "bank_details_cor_account_key", columnNames = {"cor_account"})
        })
public class BankDetails extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "bik", nullable = false)
    private Long bik;
    @NotNull
    @Column(name = "inn", nullable = false)
    private Long inn;
    @NotNull
    @Column(name = "kpp", nullable = false)
    private Long kpp;
    @NotNull
    @Column(name = "cor_account", nullable = false)
    private Long corAccount;

    @Size(max = 180)
    @NotNull
    @Column(name = "city", nullable = false, length = 180)
    private String city;
    @Size(max = 155)
    @NotNull
    @Column(name = "joint_stock_company", nullable = false, length = 155)
    private String jointStockCompany;
    @Size(max = 80)
    @NotNull
    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "bankDetails", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<License> licenses;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "bankDetails", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Certificate> certificates;

    public static BankDetails findByBik(Long bik) {
        return find("bik", bik).firstResult();
    }
}
