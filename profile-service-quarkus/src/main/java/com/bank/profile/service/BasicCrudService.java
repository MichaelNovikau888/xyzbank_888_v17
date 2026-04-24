package com.bank.profile.service;

import java.util.List;

public interface BasicCrudService<TDto> {
    List<TDto> getAll();
    TDto get(Long id);
    TDto create(TDto entity);
    TDto update(Long id, TDto entity);
    void delete(Long id);
}
