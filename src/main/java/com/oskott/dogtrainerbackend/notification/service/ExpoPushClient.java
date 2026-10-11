package com.oskott.dogtrainerbackend.notification.service;

import java.util.List;
import java.util.Map;

/**
 * Thin abstraction over the Expo Push API so delivery logic can be tested without network access.
 */
public interface ExpoPushClient {

    /**
     * Sends the messages and returns one ticket per message, in the same order. Throws
     * {@link ExpoPushException} if the request as a whole failed (no tickets were issued).
     */
    List<Ticket> send(List<Message> messages);

    /** Receipts keyed by ticket id; tickets Expo has no receipt for (yet) are absent. */
    Map<String, Receipt> getReceipts(List<String> ticketIds);

    record Message(String to, String title, String body, Map<String, Object> data, Integer badge) {
    }

    /** {@code id} is set when Expo accepted the message; {@code error} (e.g. DeviceNotRegistered) otherwise. */
    record Ticket(String id, String error) {
        public boolean accepted() {
            return id != null && error == null;
        }
    }

    record Receipt(boolean ok, String error) {
    }

    class ExpoPushException extends RuntimeException {
        public ExpoPushException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}