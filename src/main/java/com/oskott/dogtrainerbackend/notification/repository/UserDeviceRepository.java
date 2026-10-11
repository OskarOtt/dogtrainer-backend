package com.oskott.dogtrainerbackend.notification.repository;

import com.oskott.dogtrainerbackend.notification.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    Optional<UserDevice> findByExpoPushToken(String expoPushToken);

    List<UserDevice> findAllByUserId(UUID userId);

    List<UserDevice> findAllByUserIdIn(Collection<UUID> userIds);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserDevice d WHERE d.expoPushToken IN :tokens")
    void deleteByExpoPushTokenIn(@Param("tokens") Collection<String> tokens);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserDevice d WHERE d.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
