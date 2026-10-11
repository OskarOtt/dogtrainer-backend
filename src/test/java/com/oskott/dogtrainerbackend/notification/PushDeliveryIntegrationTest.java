package com.oskott.dogtrainerbackend.notification;

import com.oskott.dogtrainerbackend.notification.entity.PushTicket;
import com.oskott.dogtrainerbackend.notification.repository.PushTicketRepository;
import com.oskott.dogtrainerbackend.notification.repository.UserDeviceRepository;
import com.oskott.dogtrainerbackend.notification.service.ExpoPushClient;
import com.oskott.dogtrainerbackend.notification.service.PushDeliveryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushDeliveryIntegrationTest extends AbstractNotificationIntegrationTest {

    @Autowired
    private PushDeliveryService pushDeliveryService;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private PushTicketRepository pushTicketRepository;

    @Test
    @SuppressWarnings("unchecked")
    void pushIsSentAsynchronouslyAfterTheActionCommits() throws Exception {
        TestUser followee = registerUser("pd-followee");
        TestUser follower = registerUser("pd-follower");
        String token = uniqueToken();
        registerDevice(followee, token, "IOS", "en");
        when(expoPushClient.send(anyList())).thenAnswer(invocation -> {
            List<ExpoPushClient.Message> messages = invocation.getArgument(0);
            return messages.stream().map(m -> new ExpoPushClient.Ticket("ticket-" + UUID.randomUUID(), null)).toList();
        });

        follow(follower, followee);

        ArgumentCaptor<List<ExpoPushClient.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(expoPushClient, timeout(5000)).send(captor.capture());
        ExpoPushClient.Message message = captor.getValue().get(0);
        assertThat(message.to()).isEqualTo(token);
        assertThat(message.title()).isEqualTo("New follower");
        assertThat(message.badge()).isEqualTo(1);
        assertThat(message.data()).containsEntry("type", "NEW_FOLLOWER").containsEntry("referenceId", follower.id());
    }

    @Test
    void usersWithoutDevicesAreNeverSentAPush() throws Exception {
        TestUser followee = registerUser("pd-nodevice-followee");
        TestUser follower = registerUser("pd-nodevice-follower");

        follow(follower, followee);
        Thread.sleep(300);

        verify(expoPushClient, never()).send(anyList());
        assertThat(notificationsOf(followee)).hasSize(1);
    }

    @Test
    void notificationIsPersistedAndTheActionSucceedsEvenWhenPushDeliveryFails() throws Exception {
        TestUser followee = registerUser("pd-fail-followee");
        TestUser follower = registerUser("pd-fail-follower");
        registerDevice(followee, uniqueToken(), "ANDROID", "en");
        when(expoPushClient.send(anyList())).thenThrow(new ExpoPushClient.ExpoPushException("Expo is down", null));

        follow(follower, followee);

        verify(expoPushClient, timeout(5000)).send(anyList());
        assertThat(notificationsOf(followee)).hasSize(1);
    }

    @Test
    void deviceNotRegisteredTicketsRemoveTheToken() throws Exception {
        TestUser followee = registerUser("pd-stale-followee");
        TestUser follower = registerUser("pd-stale-follower");
        String staleToken = uniqueToken();
        String goodToken = uniqueToken();
        registerDevice(followee, staleToken, "IOS", "en");
        registerDevice(followee, goodToken, "ANDROID", "en");
        when(expoPushClient.send(anyList())).thenAnswer(invocation -> {
            List<ExpoPushClient.Message> messages = invocation.getArgument(0);
            return messages.stream()
                    .map(m -> m.to().equals(staleToken)
                            ? new ExpoPushClient.Ticket(null, "DeviceNotRegistered")
                            : new ExpoPushClient.Ticket("ok-ticket-" + UUID.randomUUID(), null))
                    .toList();
        });

        follow(follower, followee);

        verify(expoPushClient, timeout(5000)).send(anyList());
        waitUntil(() -> userDeviceRepository.findByExpoPushToken(staleToken).isEmpty());
        assertThat(userDeviceRepository.findByExpoPushToken(goodToken)).isPresent();
        waitUntil(() -> pushTicketRepository.findAll().stream().anyMatch(t -> t.getExpoPushToken().equals(goodToken)));
    }

    @Test
    void receiptsRemoveInvalidTokensAndAreOnlyProcessedOnce() throws Exception {
        TestUser user = registerUser("pr-user");
        String badToken = uniqueToken();
        String goodToken = uniqueToken();
        String pendingToken = uniqueToken();
        registerDevice(user, badToken, "IOS", "en");
        registerDevice(user, goodToken, "IOS", "en");
        registerDevice(user, pendingToken, "IOS", "en");
        String badTicket = "bad-" + UUID.randomUUID();
        String goodTicket = "good-" + UUID.randomUUID();
        String pendingTicket = "pending-" + UUID.randomUUID();
        Instant old = Instant.now().minusSeconds(3600);
        pushTicketRepository.saveAll(List.of(
                new PushTicket(UUID.randomUUID(), badTicket, badToken, old),
                new PushTicket(UUID.randomUUID(), goodTicket, goodToken, old),
                new PushTicket(UUID.randomUUID(), pendingTicket, pendingToken, old)
        ));
        when(expoPushClient.getReceipts(anyList())).thenReturn(Map.of(
                badTicket, new ExpoPushClient.Receipt(false, "DeviceNotRegistered"),
                goodTicket, new ExpoPushClient.Receipt(true, null)
        ));

        pushDeliveryService.processReceipts();

        assertThat(userDeviceRepository.findByExpoPushToken(badToken)).isEmpty();
        assertThat(userDeviceRepository.findByExpoPushToken(goodToken)).isPresent();
        assertThat(userDeviceRepository.findByExpoPushToken(pendingToken)).isPresent();
        List<String> remaining = pushTicketRepository.findAll().stream().map(PushTicket::getExpoTicketId).toList();
        assertThat(remaining).contains(pendingTicket).doesNotContain(badTicket, goodTicket);
    }

    @Test
    void failedReceiptFetchKeepsTicketsForTheNextRun() {
        String ticketId = "retry-" + UUID.randomUUID();
        pushTicketRepository.save(new PushTicket(UUID.randomUUID(), ticketId, uniqueToken(), Instant.now().minusSeconds(60)));
        when(expoPushClient.getReceipts(anyList())).thenThrow(new ExpoPushClient.ExpoPushException("down", null));

        pushDeliveryService.processReceipts();

        assertThat(pushTicketRepository.findAll()).anyMatch(t -> t.getExpoTicketId().equals(ticketId));
    }

    @Test
    void ticketsOlderThanReceiptRetentionAreDiscarded() {
        String ticketId = "expired-" + UUID.randomUUID();
        pushTicketRepository.save(new PushTicket(UUID.randomUUID(), ticketId, uniqueToken(), Instant.now().minusSeconds(48 * 3600)));
        when(expoPushClient.getReceipts(anyList())).thenReturn(Map.of());

        pushDeliveryService.processReceipts();

        assertThat(pushTicketRepository.findAll()).noneMatch(t -> t.getExpoTicketId().equals(ticketId));
    }

    @Test
    void deliveringUnknownNotificationIdsIsANoOp() {
        pushDeliveryService.deliver(List.of(UUID.randomUUID()));

        verify(expoPushClient, never()).send(anyList());
    }

    private void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
