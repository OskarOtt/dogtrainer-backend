package com.oskott.dogtrainerbackend.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PushReceiptPoller {

    private static final Logger log = LoggerFactory.getLogger(PushReceiptPoller.class);

    private final PushDeliveryService pushDeliveryService;

    public PushReceiptPoller(PushDeliveryService pushDeliveryService) {
        this.pushDeliveryService = pushDeliveryService;
    }

    @Scheduled(
            initialDelayString = "${app.expo.receipt-poll-interval}",
            fixedDelayString = "${app.expo.receipt-poll-interval}"
    )
    public void poll() {
        try {
            pushDeliveryService.processReceipts();
        } catch (RuntimeException e) {
            log.warn("Processing Expo push receipts failed", e);
        }
    }
}