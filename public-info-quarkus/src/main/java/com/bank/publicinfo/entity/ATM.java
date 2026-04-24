package com.bank.publicinfo.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "atm", schema = "public_info")
public class ATM extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 370)
    @NotNull
    @Column(name = "address", nullable = false, length = 370)
    private String address;
    @Column(name = "start_of_work")
    private LocalTime startOfWork;
    @Column(name = "end_of_work")
    private LocalTime endOfWork;
    @NotNull
    @Column(name = "all_hours", nullable = false)
    private Boolean allHours;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    public static List<ATM> findByBranchId(Long branchId) {
        return list("branch.id", branchId);
    }
}
