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

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "profile", schema = "profile")
public class Profile extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "phone_number")
    private String phoneNumber;
    @Column(name = "email")
    private String email;
    @Column(name = "name_on_card")
    private String nameOnCard;
 /**
     * СНИЛС — страховой номер индивидуального лицевого счёта
 */
    @Column(name = "snils")
    private Long snils;
 /**
     * ИНН — идентификационный номер налогоплательщика
 */
    @Column(name = "inn")
    private Long inn;
    @OneToOne(cascade = CascadeType.ALL, optional = true)
    @JoinColumn(name = "passport_id")
    private Passport passport;
    @OneToOne(cascade = CascadeType.ALL, optional = true)
    @JoinColumn(name = "actual_registration_id")
    private ActualRegistration actualRegistration;
}
