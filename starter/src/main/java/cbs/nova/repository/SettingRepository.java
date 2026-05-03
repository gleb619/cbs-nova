package cbs.nova.repository;

import cbs.nova.entity.Setting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, Long> {

  Optional<Setting> findByCode(String code);
}
