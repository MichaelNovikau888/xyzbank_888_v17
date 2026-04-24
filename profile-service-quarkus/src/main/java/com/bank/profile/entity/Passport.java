package com.bank.profile.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "passport", schema = "profile")
public class Passport extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "series")
    private Long series;
    @Column(name = "number")
    private Long number;
    @Column(name = "last_name")
    private String lastName;
    @Column(name = "first_name")
    private String firstName;
    @Column(name = "middle_name")
    private String middleName;
    @Column(name = "gender")
    private String gender;
    @Column(name = "birth_date")
    private LocalDate birthDate;
    @Column(name = "birth_place")
    private String birthPlace;
    @Column(name = "issued_by")
    private String issuedBy;
    @Column(name = "date_of_issue")
    private LocalDate dateOfIssue;
    @Column(name = "division_code")
    private Integer divisionCode;
    @Column(name = "expiration_date")
    private LocalDate expirationDate;
    @OneToOne(cascade = CascadeType.ALL, optional = true)
    @JoinColumn(name = "registration_id")
    private Registration registration;
}
