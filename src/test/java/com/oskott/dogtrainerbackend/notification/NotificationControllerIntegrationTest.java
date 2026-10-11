package com.oskott.dogtrainerbackend.notification;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerIntegrationTest extends AbstractNotificationIntegrationTest {

    @Test
    void followingCreatesANewFollowerNotificationForTheFolloweeOnly() throws Exception {
        TestUser follower = registerUser("nf-follower");
        TestUser followee = registerUser("nf-followee");

        follow(follower, followee);

        JsonNode notifications = notificationsOf(followee);
        assertThat(notifications).hasSize(1);
        JsonNode notification = notifications.get(0);
        assertThat(notification.get("type").asString()).isEqualTo("NEW_FOLLOWER");
        assertThat(notification.get("referenceId").asString()).isEqualTo(follower.id());
        assertThat(notification.get("actor").get("id").asString()).isEqualTo(follower.id());
        assertThat(notification.get("read").asBoolean()).isFalse();
        assertThat(notification.get("title").asString()).isEqualTo("New follower");
        assertThat(notificationsOf(follower)).isEmpty();
    }

    @Test
    void refollowingNeverNotifiesTheSameFollowerTwice() throws Exception {
        TestUser follower = registerUser("nf-refollow-a");
        TestUser followee = registerUser("nf-refollow-b");

        follow(follower, followee);
        unfollow(follower, followee);
        follow(follower, followee);

        assertThat(notificationsOf(followee)).hasSize(1);
    }

    @Test
    void newPostNotifiesFollowersOnlyAndNeverTheAuthor() throws Exception {
        TestUser author = registerUser("np-author");
        TestUser follower = registerUser("np-follower");
        TestUser stranger = registerUser("np-stranger");
        follow(follower, author);
        follow(author, follower);

        String postId = createPost(author);

        JsonNode followerNotifications = notificationsOf(follower);
        assertThat(followerNotifications).hasSize(2);
        JsonNode newPost = followerNotifications.get(0);
        assertThat(newPost.get("type").asString()).isEqualTo("NEW_POST");
        assertThat(newPost.get("referenceId").asString()).isEqualTo(postId);
        assertThat(newPost.get("actor").get("id").asString()).isEqualTo(author.id());
        assertThat(notificationsOf(stranger)).isEmpty();
        // the author only has the NEW_FOLLOWER notification from follower, nothing about their own post
        JsonNode authorNotifications = notificationsOf(author);
        assertThat(authorNotifications).hasSize(1);
        assertThat(authorNotifications.get(0).get("type").asString()).isEqualTo("NEW_FOLLOWER");
    }

    @Test
    void newPostDoesNotNotifyAfterUnfollowOrWhenBlocked() throws Exception {
        TestUser author = registerUser("np2-author");
        TestUser unfollowed = registerUser("np2-unfollowed");
        TestUser blocked = registerUser("np2-blocked");
        follow(unfollowed, author);
        follow(blocked, author);
        unfollow(unfollowed, author);
        block(author, blocked);

        createPost(author);

        assertThat(notificationsOf(unfollowed)).isEmpty();
        assertThat(notificationsOf(blocked)).isEmpty();
    }

    @Test
    void commentNotifiesThePostOwnerButNotForTheirOwnComments() throws Exception {
        TestUser owner = registerUser("cm-owner");
        TestUser commenter = registerUser("cm-commenter");
        String postId = createPost(owner);

        comment(commenter, postId);
        comment(owner, postId);

        JsonNode notifications = notificationsOf(owner);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).get("type").asString()).isEqualTo("COMMENT");
        assertThat(notifications.get(0).get("referenceId").asString()).isEqualTo(postId);
        assertThat(notifications.get(0).get("body").asString()).doesNotContain("Nice!");
        assertThat(notificationsOf(commenter)).isEmpty();
    }

    @Test
    void everyCommentNotifiesSeparately() throws Exception {
        TestUser owner = registerUser("cm2-owner");
        TestUser commenter = registerUser("cm2-commenter");
        String postId = createPost(owner);

        comment(commenter, postId);
        comment(commenter, postId);

        assertThat(notificationsOf(owner)).hasSize(2);
    }

    @Test
    void commentFromABlockedUserDoesNotNotify() throws Exception {
        TestUser owner = registerUser("cm3-owner");
        TestUser commenter = registerUser("cm3-commenter");
        String postId = createPost(owner);
        block(owner, commenter);

        comment(commenter, postId);

        assertThat(notificationsOf(owner)).isEmpty();
    }

    @Test
    void likesDoNotCreateNotifications() throws Exception {
        TestUser owner = registerUser("lk-owner");
        TestUser liker = registerUser("lk-liker");
        String postId = createPost(owner);

        mockMvc.perform(post("/api/v1/posts/" + postId + "/like")
                        .header("Authorization", bearer(liker)))
                .andExpect(status().is2xxSuccessful());

        assertThat(notificationsOf(owner)).isEmpty();
    }

    @Test
    void deletingAPostRemovesItsNotifications() throws Exception {
        TestUser author = registerUser("dp-author");
        TestUser follower = registerUser("dp-follower");
        follow(follower, author);
        String postId = createPost(author);
        assertThat(notificationsOf(follower)).hasSize(1);
        assertThat(notificationsOf(author)).hasSize(1);

        mockMvc.perform(delete("/api/v1/posts/" + postId)
                        .header("Authorization", bearer(author)))
                .andExpect(status().isNoContent());

        assertThat(notificationsOf(follower)).isEmpty();
        JsonNode authorNotifications = notificationsOf(author);
        assertThat(authorNotifications).hasSize(1);
        assertThat(authorNotifications.get(0).get("type").asString()).isEqualTo("NEW_FOLLOWER");
    }

    @Test
    void notificationsAreListedWithCursorPaginationAndOnlyForTheCaller() throws Exception {
        TestUser followee = registerUser("pg-followee");
        TestUser other = registerUser("pg-other");
        for (int i = 0; i < 3; i++) {
            follow(registerUser("pg-follower" + i), followee);
        }

        JsonNode firstPage = readBody(mockMvc.perform(get("/api/v1/notifications")
                        .param("limit", "2")
                        .header("Authorization", bearer(followee)))
                .andExpect(status().isOk()));
        assertThat(firstPage.get("items")).hasSize(2);
        assertThat(firstPage.get("nextCursor").isNull()).isFalse();

        JsonNode secondPage = readBody(mockMvc.perform(get("/api/v1/notifications")
                        .param("limit", "2")
                        .param("cursor", firstPage.get("nextCursor").asString())
                        .header("Authorization", bearer(followee)))
                .andExpect(status().isOk()));
        assertThat(secondPage.get("items")).hasSize(1);
        assertThat(secondPage.get("nextCursor").isNull()).isTrue();
        assertThat(secondPage.get("items").get(0).get("id").asString())
                .isNotEqualTo(firstPage.get("items").get(0).get("id").asString())
                .isNotEqualTo(firstPage.get("items").get(1).get("id").asString());

        assertThat(notificationsOf(other)).isEmpty();
    }

    @Test
    void markReadAndUnreadCountOnlyAffectTheOwner() throws Exception {
        TestUser followee = registerUser("rd-followee");
        TestUser intruder = registerUser("rd-intruder");
        follow(registerUser("rd-follower1"), followee);
        follow(registerUser("rd-follower2"), followee);
        assertThat(unreadCountOf(followee)).isEqualTo(2);
        String notificationId = notificationsOf(followee).get(0).get("id").asString();

        mockMvc.perform(post("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isNotFound());
        assertThat(unreadCountOf(followee)).isEqualTo(2);

        mockMvc.perform(post("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", bearer(followee)))
                .andExpect(status().isNoContent());
        assertThat(unreadCountOf(followee)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isNoContent());
        assertThat(unreadCountOf(followee)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .header("Authorization", bearer(followee)))
                .andExpect(status().isNoContent());
        assertThat(unreadCountOf(followee)).isZero();
        assertThat(unreadCountOf(intruder)).isZero();
    }

    @Test
    void notificationEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/v1/notifications/read-all")).andExpect(status().is4xxClientError());
    }

    @Test
    void notificationTextFollowsTheRecipientsDeviceLocale() throws Exception {
        TestUser followee = registerUser("lc-followee");
        TestUser follower = registerUser("lc-follower");
        registerDevice(followee, uniqueToken(), "IOS", "nb-NO");

        follow(follower, followee);

        JsonNode notification = notificationsOf(followee).get(0);
        assertThat(notification.get("title").asString()).isEqualTo("Ny følger");
        assertThat(notification.get("body").asString()).contains("begynte å følge deg");
    }

    @Test
    void invalidCursorIsRejected() throws Exception {
        TestUser user = registerUser("cur");
        mockMvc.perform(get("/api/v1/notifications")
                        .param("cursor", "not-a-cursor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", bearer(user)))
                .andExpect(status().isConflict());
    }
}
