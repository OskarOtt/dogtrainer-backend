package com.oskott.dogtrainerbackend.notification.controller;

import com.oskott.dogtrainerbackend.notification.dto.RegisterDeviceRequest;
import com.oskott.dogtrainerbackend.notification.dto.UnregisterDeviceRequest;
import com.oskott.dogtrainerbackend.notification.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PutMapping
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterDeviceRequest request) {
        deviceService.register(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unregister(@Valid @RequestBody UnregisterDeviceRequest request) {
        deviceService.unregister(request.expoPushToken());
        return ResponseEntity.noContent().build();
    }
}