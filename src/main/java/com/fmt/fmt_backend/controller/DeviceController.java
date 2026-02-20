package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.service.AuthService;
import com.fmt.fmt_backend.service.DeviceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceController {

    private final DeviceService deviceService;
    private final AuthService authService;
    private final HttpServletRequest request;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMyDevices() {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<Map<String, Object>> devices = deviceService.getUserDevices(currentUser, request);

        return ResponseEntity.ok(ApiResponse.success("Devices retrieved", devices));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<String>> revokeDevice(@PathVariable UUID deviceId) {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        ApiResponse<String> response = deviceService.revokeDevice(currentUser, deviceId);

        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/check-limit")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> checkDeviceLimit() {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        ApiResponse<List<Map<String, Object>>> response = deviceService.handleDeviceLimit(currentUser, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/disconnect")
    public ResponseEntity<ApiResponse<String>> disconnectDevice(@RequestParam UUID deviceId) {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        ApiResponse<String> response = deviceService.disconnectDevice(currentUser, deviceId);

        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/streaming/start")
    public ResponseEntity<ApiResponse<String>> startStreaming(@RequestParam UUID deviceId) {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        ApiResponse<String> response = deviceService.startStreaming(currentUser, deviceId);

        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/streaming/stop")
    public ResponseEntity<ApiResponse<String>> stopStreaming(@RequestParam UUID deviceId) {
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        ApiResponse<String> response = deviceService.stopStreaming(currentUser, deviceId);

        return ResponseEntity.ok(response);
    }
}