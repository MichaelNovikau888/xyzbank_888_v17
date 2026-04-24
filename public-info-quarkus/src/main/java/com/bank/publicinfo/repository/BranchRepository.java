package com.bank.publicinfo.repository;

import com.bank.publicinfo.entity.Branch;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Репозиторий отделений банка.
 * В оригинале Spring Data — пустой JpaRepository<Branch, Long>,
 * все нужные методы наследуются. В Quarkus — PanacheRepository с теми же возможностями.
 */
@ApplicationScoped
public class BranchRepository implements PanacheRepository<Branch> {
    // Все базовые операции (findById, persist, delete, listAll, count...)
    // предоставляются PanacheRepository — явно объявлять не нужно.
}
