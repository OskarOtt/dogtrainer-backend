package com.oskott.dogtrainerbackend.notification.service;

import com.oskott.dogtrainerbackend.common.i18n.SupportedLocale;
import com.oskott.dogtrainerbackend.notification.entity.NotificationType;

/**
 * Title/body copy per notification type, rendered in the recipient's locale (not the locale of the
 * request that triggered the event). Comment text is deliberately never included in a push body.
 */
final class NotificationTexts {

    private static final int MAX_ACTOR_NAME_LENGTH = 100;

    record Text(String title, String body) {
    }

    private NotificationTexts() {
    }

    static Text render(NotificationType type, String locale, String actorName) {
        String name = actorName.length() > MAX_ACTOR_NAME_LENGTH
                ? actorName.substring(0, MAX_ACTOR_NAME_LENGTH)
                : actorName;
        boolean bokmal = SupportedLocale.BOKMAL.equals(locale);
        return switch (type) {
            case NEW_POST -> bokmal
                    ? new Text("Nytt innlegg", name + " la ut et nytt innlegg")
                    : new Text("New post", name + " shared a new post");
            case COMMENT -> bokmal
                    ? new Text("Ny kommentar", name + " kommenterte på innlegget ditt")
                    : new Text("New comment", name + " commented on your post");
            case NEW_FOLLOWER -> bokmal
                    ? new Text("Ny følger", name + " begynte å følge deg")
                    : new Text("New follower", name + " started following you");
        };
    }
}