package cbs.nova.repository;

import cbs.nova.entity.SettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettingRepository extends JpaRepository<SettingEntity, Long> {

  Optional<SettingEntity> findByCode(String code);
}
