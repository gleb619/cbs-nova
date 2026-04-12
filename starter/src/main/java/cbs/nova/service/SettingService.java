package cbs.nova.service;

import cbs.nova.entity.SettingEntity;
import cbs.nova.mapper.SettingMapper;
import cbs.nova.model.SettingCreateDto;
import cbs.nova.model.SettingDto;
import cbs.nova.model.SettingUpdateDto;
import cbs.nova.model.exception.EntityNotFoundException;
import cbs.nova.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingService {

  private final SettingRepository repository;
  private final SettingMapper mapper;

  public List<SettingDto> findAll() {
    log.debug("Finding all settings");
    return repository.findAll().stream().map(mapper::toDto).toList();
  }

  public SettingDto findById(Long id) {
    log.debug("Finding setting by id: {}", id);
    return repository
        .findById(id)
        .map(mapper::toDto)
        .orElseThrow(() -> new EntityNotFoundException("Entity", id));
  }

  public SettingDto findByCode(String code) {
    log.debug("Finding setting by code: {}", code);
    return repository
        .findByCode(code)
        .map(mapper::toDto)
        .orElseThrow(() -> new EntityNotFoundException("Entity", code));
  }

  @Transactional
  public SettingDto create(SettingCreateDto dto) {
    log.debug("Creating setting: {}", dto);
    SettingEntity entity = mapper.toEntity(dto);
    return mapper.toDto(repository.save(entity));
  }

  @Transactional
  public SettingDto update(Long id, SettingUpdateDto dto) {
    log.debug("Updating setting with id: {}, dto: {}", id, dto);
    SettingEntity entity =
        repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Entity", id));
    mapper.update(dto, entity);
    return mapper.toDto(repository.save(entity));
  }

  @Transactional
  public void delete(Long id) {
    log.debug("Deleting setting with id: {}", id);
    repository.deleteById(id);
  }
}
