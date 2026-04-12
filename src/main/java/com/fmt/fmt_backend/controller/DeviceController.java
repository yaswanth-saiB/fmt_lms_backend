package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.service.AuthService;
import com.fmt.fmt_backend.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Device Management", description = "APIs for managing user devices and streaming")
public class DeviceController {

    private final DeviceService deviceService;
    private final AuthService authService;
    private final HttpServletRequest request;

    @GetMapping
    @Operation(
            summary = "Get All Devices",
            description = "Returns list of all devices associated with current user"
    )
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMyDevices() {
        log.info("📱 Fetching devices for current user");
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        List<Map<String, Object>> devices = deviceService.getUserDevices(currentUser, request);

        return ResponseEntity.ok(ApiResponse.success("Devices retrieved", devices));
    }

    @DeleteMapping("/{deviceId}")
    @Operation(
            summary = "Revoke Device",
            description = "Revoke a specific device (force logout)"
    )
    public ResponseEntity<ApiResponse<String>> revokeDevice(
            @Parameter(description = "Device ID to revoke", required = true)
            @PathVariable UUID deviceId) {

        log.info("🔒 Revoking device: {}", deviceId);

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        ApiResponse<String> response = deviceService.revokeDevice(currentUser, deviceId);

        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/check-limit")
    @Operation(
            summary = "Check Device Limit",
            description = "Check if user has exceeded device limit (max 1 device — only one active session allowed)"
    )
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> checkDeviceLimit() {
        log.info("🔍 Checking device limit for current user");
        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        ApiResponse<List<Map<String, Object>>> response = deviceService.handleDeviceLimit(currentUser, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/disconnect")
    @Operation(
            summary = "Disconnect Device",
            description = "Disconnect a specific device (user choice)"
    )
    public ResponseEntity<ApiResponse<String>> disconnectDevice(
            @Parameter(description = "Device ID to disconnect", required = true)
            @RequestParam UUID deviceId) {
        log.info("🔌 Disconnecting device: {}", deviceId);

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        ApiResponse<String> response = deviceService.disconnectDevice(currentUser, deviceId);

        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/streaming/start")
    @Operation(
            summary = "Start Streaming",
            description = "Start streaming on specified device (only 1 device can stream at a time)"
    )
    public ResponseEntity<ApiResponse<String>> startStreaming(
            @Parameter(description = "Device ID to start streaming", required = true)
            @RequestParam UUID deviceId) {
        log.info("🎥 Starting streaming on device: {}", deviceId);

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        ApiResponse<String> response = deviceService.startStreaming(currentUser, deviceId);

        return ResponseEntity.status(response.isSuccess() ? 200 : 400).body(response);
    }

    @PostMapping("/streaming/stop")
    @Operation(
            summary = "Stop Streaming",
            description = "Stop streaming on specified device"
    )
    public ResponseEntity<ApiResponse<String>> stopStreaming(
            @Parameter(description = "Device ID to stop streaming", required = true)
            @RequestParam UUID deviceId) {
        log.info("⏹️ Stopping streaming on device: {}", deviceId);

        User currentUser = authService.getCurrentUser()
                .orElseThrow(() -> new RuntimeException("Not authenticated"));

        ApiResponse<String> response = deviceService.stopStreaming(currentUser, deviceId);

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<String>> handleRuntimeException(RuntimeException e) {
        log.error("❌ Device controller error: {}", e.getMessage());
        return ResponseEntity.status(401)
                .body(ApiResponse.error(e.getMessage()));
    }
}