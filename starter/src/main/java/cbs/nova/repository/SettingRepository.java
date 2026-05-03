package cbs.nova.repository;

import cbs.nova.entity.SettingEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<SettingEntity, Long> {

  Optional<SettingEntity> findByCode(String code);
}
