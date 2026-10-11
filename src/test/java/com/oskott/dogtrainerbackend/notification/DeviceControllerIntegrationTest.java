package com.oskott.dogtrainerbackend.notification;

import com.oskott.dogtrainerbackend.notification.entity.DevicePlatform;
import com.oskott.dogtrainerbackend.notification.entity.UserDevice;
import com.oskott.dogtrainerbackend.notification.repository.UserDeviceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviceControllerIntegrationTest extends AbstractNotificationIntegrationTest {

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Test
    void registeringTheSameTokenTwiceKeepsASingleDeviceAndUpdatesIt() throws Exception {
        TestUser user = registerUser("dev-upsert");
        String token = uniqueToken();

        registerDevice(user, token, "IOS", "en");
        registerDevice(user, token, "ANDROID", "nb");

        UserDevice device = userDeviceRepository.findByExpoPushToken(token).orElseThrow();
        assertThat(userDeviceRepository.findAllByUserId(UUID.fromString(user.id()))).hasSize(1);
        assertThat(device.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
        assertThat(device.getLocale()).isEqualTo("nb");
        assertThat(device.getUserId()).isEqualTo(UUID.fromString(user.id()));
    }

    @Test
    void aTokenRegisteredByAnotherUserIsReassignedNotDuplicated() throws Exception {
        TestUser first = registerUser("dev-first");
        TestUser second = registerUser("dev-second");
        String token = uniqueToken();

        registerDevice(first, token, "IOS", "en");
        registerDevice(second, token, "IOS", "en");

        assertThat(userDeviceRepository.findByExpoPushToken(token).orElseThrow().getUserId())
                .isEqualTo(UUID.fromString(second.id()));
        assertThat(userDeviceRepository.findAllByUserId(UUID.fromString(first.id()))).isEmpty();
    }

    @Test
    void theTokenIsAlwaysBoundToTheAuthenticatedUserNeverAClientSuppliedId() throws Exception {
        TestUser user = registerUser("dev-owner");
        TestUser victim = registerUser("dev-victim");
        String token = uniqueToken();

        mockMvc.perform(put("/api/v1/devices")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\":\"" + token + "\",\"platform\":\"IOS\",\"userId\":\""
                                + victim.id() + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(userDeviceRepository.findByExpoPushToken(token).orElseThrow().getUserId())
                .isEqualTo(UUID.fromString(user.id()));
    }

    @Test
    void invalidTokensAndPlatformsAreRejected() throws Exception {
        TestUser user = registerUser("dev-invalid");

        mockMvc.perform(put("/api/v1/devices")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\":\"not-an-expo-token\",\"platform\":\"IOS\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/devices")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\":\"" + uniqueToken() + "\",\"platform\":\"WINDOWS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrationRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\":\"" + uniqueToken() + "\",\"platform\":\"IOS\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void unregisterRemovesOnlyTheCallersOwnToken() throws Exception {
        TestUser owner = registerUser("dev-unreg-owner");
        TestUser other = registerUser("dev-unreg-other");
        String token = uniqueToken();
        registerDevice(owner, token, "IOS", "en");
        String body = "{\"expoPushToken\":\"" + token + "\"}";

        mockMvc.perform(delete("/api/v1/devices")
                        .header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
        assertThat(userDeviceRepository.findByExpoPushToken(token)).isPresent();

        mockMvc.perform(delete("/api/v1/devices")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
        assertThat(userDeviceRepository.findByExpoPushToken(token)).isEmpty();
    }

    @Test
    void deletingAnAccountRemovesItsDevicesAndNotifications() throws Exception {
        TestUser user = registerUser("dev-delete");
        TestUser follower = registerUser("dev-delete-follower");
        String token = uniqueToken();
        registerDevice(user, token, "IOS", "en");
        follow(follower, user);
        assertThat(notificationsOf(user)).hasSize(1);

        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"PASSWORD\",\"password\":\"SuperSecret123\"}"))
                .andExpect(status().isNoContent());

        assertThat(userDeviceRepository.findByExpoPushToken(token)).isEmpty();
    }
}
