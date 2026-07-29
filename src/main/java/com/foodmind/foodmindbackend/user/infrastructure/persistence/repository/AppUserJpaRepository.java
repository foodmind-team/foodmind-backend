package com.foodmind.foodmindbackend.user.infrastructure.persistence.repository;

import com.foodmind.foodmindbackend.user.infrastructure.persistence.entity.AppUserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @description:
 * @author: chenyaqi
 * @email: terrence.yaqi.chen@u.nus.edu
 * @date: 29/7/2026 8:00 pm
 */

public interface AppUserJpaRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByNormalisedEmail(String normalisedEmail);

    boolean existsByNormalisedEmail(String normalisedEmail);
}
