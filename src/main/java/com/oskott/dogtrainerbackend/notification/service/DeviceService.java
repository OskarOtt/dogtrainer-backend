package com.oskott.dogtrainerbackend.notification.service;

import com.oskott.dogtrainerbackend.common.i18n.SupportedLocale;
import com.oskott.dogtrainerbackend.common.security.CurrentUserProvider;
import com.oskott.dogtrainerbackend.notification.dto.RegisterDeviceRequest;
import com.oskott.dogtrainerbackend.notification.entity.UserDevice;
import com.oskott.dogtrainerbackend.notification.repository.UserDeviceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class DeviceService {

    private final UserDeviceRepository userDeviceRepository;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate transactionTemplate;

    public DeviceService(
            UserDeviceRepository userDeviceRepository,
            CurrentUserProvider currentUserProvider,
            PlatformTransactionManager transactionManager
    ) {
        this.userDeviceRepository = userDeviceRepository;
        this.currentUserProvider = currentUserProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Registers the token for the authenticated user. An Expo token identifies an app install, so
     * a token already known for another user (shared device, switched account) is re-assigned
     * rather than duplicated. If two concurrent registrations race on the unique token constraint,
     * the loser retries and takes the update path.
     */
    public void register(RegisterDeviceRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();
        String locale = normalizeLocale(request.locale());
        try {
            transactionTemplate.executeWithoutResult(status -> upsert(userId, request, locale));
        } catch (DataIntegrityViolationException e) {
            transactionTemplate.executeWithoutResult(status -> upsert(userId, request, locale));
        }
    }

    /** Only removes a token that belongs to the caller; anything else is a silent no-op. */
    @Transactional
    public void unregister(String expoPushToken) {
        UUID userId = currentUserProvider.getCurrentUserId();
        userDeviceRepository.findByExpoPushToken(expoPushToken)
                .filter(device -> device.getUserId().equals(userId))
                .ifPresent(userDeviceRepository::delete);
    }

    private void upsert(UUID userId, RegisterDeviceRequest request, String locale) {
        Instant now = Instant.now();
        userDeviceRepository.findByExpoPushToken(request.expoPushToken()).ifPresentOrElse(
                device -> device.reassign(userId, request.devicePlatform(), locale, now),
                () -> userDeviceRepository.saveAndFlush(new UserDevice(
                        UUID.randomUUID(), userId, request.expoPushToken(), request.devicePlatform(), locale, now))
        );
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return SupportedLocale.ENGLISH;
        }
        String language = Locale.forLanguageTag(locale.replace('_', '-')).getLanguage();
        boolean norwegian = SupportedLocale.BOKMAL.equalsIgnoreCase(language)
                || "no".equalsIgnoreCase(language)
                || "nn".equalsIgnoreCase(language);
        return norwegian ? SupportedLocale.BOKMAL : SupportedLocale.ENGLISH;
    }
}