package cbs.nova.service;

import cbs.nova.entity.Setting;
import cbs.nova.mapper.SettingMapper;
import cbs.nova.model.SettingCreateDto;
import cbs.nova.model.SettingDto;
import cbs.nova.model.exception.SettingNotFoundException;
import cbs.nova.repository.SettingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingService {

  private final SettingRepository repository;
  private final SettingMapper mapper;

  public List<SettingDto> findAll() {
    return repository.findAll().stream().map(mapper::toDto).toList();
  }

  public SettingDto findById(Long id) {
    return repository
        .findById(id)
        .map(mapper::toDto)
        .orElseThrow(() -> new SettingNotFoundException(id));
  }

  public SettingDto findByCode(String code) {
    return repository
        .findByCode(code)
        .map(mapper::toDto)
        .orElseThrow(() -> new SettingNotFoundException(code));
  }

  @Transactional
  public SettingDto create(SettingCreateDto dto) {
    Setting entity = mapper.toEntity(dto);
    return mapper.toDto(repository.save(entity));
  }

  @Transactional
  public SettingDto update(Long id, SettingCreateDto dto) {
    Setting entity = repository.findById(id).orElseThrow(() -> new SettingNotFoundException(id));
    mapper.update(dto, entity);
    return mapper.toDto(repository.save(entity));
  }

  @Transactional
  public void delete(Long id) {
    repository.deleteById(id);
  }
}
