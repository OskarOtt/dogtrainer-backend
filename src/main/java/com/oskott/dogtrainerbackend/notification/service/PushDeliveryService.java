package com.oskott.dogtrainerbackend.notification.service;

import com.oskott.dogtrainerbackend.notification.config.ExpoPushProperties;
import com.oskott.dogtrainerbackend.notification.config.PushConfig;
import com.oskott.dogtrainerbackend.notification.entity.Notification;
import com.oskott.dogtrainerbackend.notification.entity.PushTicket;
import com.oskott.dogtrainerbackend.notification.entity.UserDevice;
import com.oskott.dogtrainerbackend.notification.repository.NotificationRepository;
import com.oskott.dogtrainerbackend.notification.repository.PushTicketRepository;
import com.oskott.dogtrainerbackend.notification.repository.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sends already-persisted notifications through the Expo Push API and processes the resulting
 * tickets/receipts. Nothing in here may fail the action that caused a notification: delivery
 * problems are logged and the notification stays available in the in-app history.
 */
@Service
public class PushDeliveryService {

    static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";
    static final int RECEIPT_BATCH_SIZE = 1000;
    /** Expo only keeps receipts for 24 hours. */
    static final Duration RECEIPT_RETENTION = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(PushDeliveryService.class);

    private final NotificationRepository notificationRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final PushTicketRepository pushTicketRepository;
    private final ExpoPushClient expoPushClient;
    private final ExpoPushProperties properties;
    private final TransactionTemplate transactionTemplate;

    public PushDeliveryService(
            NotificationRepository notificationRepository,
            UserDeviceRepository userDeviceRepository,
            PushTicketRepository pushTicketRepository,
            ExpoPushClient expoPushClient,
            ExpoPushProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.notificationRepository = notificationRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.pushTicketRepository = pushTicketRepository;
        this.expoPushClient = expoPushClient;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async(PushConfig.PUSH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPushRequested(PushRequestedEvent event) {
        try {
            deliver(event.notificationIds());
        } catch (RuntimeException e) {
            log.warn("Push delivery failed unexpectedly; notifications remain available in-app", e);
        }
    }

    public void deliver(Collection<UUID> notificationIds) {
        List<Notification> notifications = notificationRepository.findAllById(notificationIds);
        if (notifications.isEmpty()) {
            return;
        }
        Set<UUID> recipientIds = notifications.stream().map(Notification::getRecipientId).collect(Collectors.toSet());
        Map<UUID, List<UserDevice>> devicesByUser = userDeviceRepository.findAllByUserIdIn(recipientIds).stream()
                .collect(Collectors.groupingBy(UserDevice::getUserId));

        Map<UUID, Integer> badgeByUser = new HashMap<>();
        List<ExpoPushClient.Message> messages = new ArrayList<>();
        for (Notification notification : notifications) {
            List<UserDevice> devices = devicesByUser.getOrDefault(notification.getRecipientId(), List.of());
            if (devices.isEmpty()) {
                continue;
            }
            int badge = badgeByUser.computeIfAbsent(notification.getRecipientId(), this::unreadBadge);
            Map<String, Object> data = Map.of(
                    "type", notification.getType().name(),
                    "referenceId", notification.getReferenceId().toString(),
                    "notificationId", notification.getId().toString()
            );
            for (UserDevice device : devices) {
                messages.add(new ExpoPushClient.Message(
                        device.getExpoPushToken(), notification.getTitle(), notification.getBody(), data, badge));
            }
        }
        if (messages.isEmpty()) {
            return;
        }

        List<ExpoPushClient.Ticket> tickets;
        try {
            tickets = expoPushClient.send(messages);
        } catch (ExpoPushClient.ExpoPushException e) {
            log.warn("Expo push request failed for {} message(s): {}", messages.size(), e.getMessage());
            return;
        }

        Instant now = Instant.now();
        List<PushTicket> accepted = new ArrayList<>();
        Set<String> invalidTokens = new HashSet<>();
        for (int i = 0; i < messages.size() && i < tickets.size(); i++) {
            ExpoPushClient.Ticket ticket = tickets.get(i);
            String token = messages.get(i).to();
            if (ticket.accepted()) {
                accepted.add(new PushTicket(UUID.randomUUID(), ticket.id(), token, now));
            } else if (DEVICE_NOT_REGISTERED.equals(ticket.error())) {
                invalidTokens.add(token);
            } else {
                log.warn("Expo rejected a push ticket: {}", ticket.error());
            }
        }
        pushTicketRepository.saveAll(accepted);
        removeDevices(invalidTokens);
    }

    /**
     * Fetches receipts for tickets older than the configured delay. Tickets are claimed with
     * {@code SKIP LOCKED} so several backend replicas can run this concurrently without
     * double-processing. Tickets whose receipt is not available yet stay for the next run.
     */
    public void processReceipts() {
        Instant now = Instant.now();
        transactionTemplate.executeWithoutResult(status -> {
            List<PushTicket> tickets = pushTicketRepository.findByCreatedAtBeforeOrderByCreatedAtAsc(
                    now.minus(properties.receiptDelay()), Limit.of(RECEIPT_BATCH_SIZE));
            if (tickets.isEmpty()) {
                return;
            }
            Map<String, ExpoPushClient.Receipt> receipts;
            try {
                receipts = expoPushClient.getReceipts(tickets.stream().map(PushTicket::getExpoTicketId).toList());
            } catch (ExpoPushClient.ExpoPushException e) {
                log.warn("Fetching Expo push receipts failed: {}", e.getMessage());
                return;
            }
            List<PushTicket> handled = new ArrayList<>();
            Set<String> invalidTokens = new HashSet<>();
            for (PushTicket ticket : tickets) {
                ExpoPushClient.Receipt receipt = receipts.get(ticket.getExpoTicketId());
                if (receipt == null) {
                    continue;
                }
                handled.add(ticket);
                if (!receipt.ok()) {
                    if (DEVICE_NOT_REGISTERED.equals(receipt.error())) {
                        invalidTokens.add(ticket.getExpoPushToken());
                    } else {
                        log.warn("Expo push delivery failed: {}", receipt.error());
                    }
                }
            }
            pushTicketRepository.deleteAll(handled);
            removeDevices(invalidTokens);
        });
        pushTicketRepository.deleteOlderThan(now.minus(RECEIPT_RETENTION));
    }

    private int unreadBadge(UUID userId) {
        return (int) Math.min(notificationRepository.countByRecipientIdAndReadFalse(userId), Integer.MAX_VALUE);
    }

    private void removeDevices(Set<String> tokens) {
        if (!tokens.isEmpty()) {
            userDeviceRepository.deleteByExpoPushTokenIn(tokens);
            log.info("Removed {} invalid Expo push token(s)", tokens.size());
        }
    }
}