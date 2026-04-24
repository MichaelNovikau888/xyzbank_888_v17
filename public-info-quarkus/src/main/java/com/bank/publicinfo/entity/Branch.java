package com.bank.publicinfo.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalTime;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "branch", schema = "public_info",
        uniqueConstraints = {
                @UniqueConstraint(name = "branch_phone_number_key", columnNames = {"phone_number"})
        })
public class Branch extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 370)
    @NotNull
    @Column(name = "address", nullable = false, length = 370)
    private String address;
    @NotNull
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;
    @Size(max = 250)
    @NotNull
    @Column(name = "city", nullable = false, length = 250)
    private String city;
    @NotNull
    @Column(name = "start_of_work", nullable = false)
    private LocalTime startOfWork;
    @NotNull
    @Column(name = "end_of_work", nullable = false)
    private LocalTime endOfWork;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "branch", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ATM> atms;
}
