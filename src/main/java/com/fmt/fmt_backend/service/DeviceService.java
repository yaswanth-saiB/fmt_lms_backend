package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.entity.DeviceEntity;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.repository.DeviceRepository;
import com.fmt.fmt_backend.repository.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${device.max-sessions-per-user:2}")
    private int maxSessionsPerUser;

    @Value("${device.max-streaming-sessions:1}")
    private int maxStreamingSessions;

    /**
     * Generate device fingerprint from request
     */
    public String generateDeviceFingerprint(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String ip = getClientIp(request);

        // Create unique fingerprint from userAgent + IP
        String fingerprintData = (userAgent != null ? userAgent : "unknown") + "|" + ip;
        String fingerprint = UUID.nameUUIDFromBytes(fingerprintData.getBytes()).toString();

        log.debug("🖐️ Generated fingerprint: {} for IP: {}", fingerprint, maskIpAddress(ip));
        return fingerprint;
    }

    /**
     * Register a new device for a user
     */
    @Transactional
    public DeviceEntity registerDevice(User user, String deviceFingerprint, HttpServletRequest request) {
        log.info("📱 Registering device for user: {}", user.getEmail());

        // Check if device already exists
        Optional<DeviceEntity> existingDeviceOpt = deviceRepository
                .findByUserAndDeviceFingerprint(user, deviceFingerprint);

        if (existingDeviceOpt.isPresent()) {
            DeviceEntity existingDevice = existingDeviceOpt.get();

            // ✅ FIX BUG 8: Reactivate if inactive
            if (!existingDevice.isActive()) {
                log.info("🔄 Reactivating inactive device: {}", existingDevice.getId());
                existingDevice.setActive(true);
            }

            // Update last active time
            existingDevice.setLastActiveAt(LocalDateTime.now());
            existingDevice.setIpAddress(getClientIp(request));
            existingDevice.setUserAgent(request.getHeader("User-Agent"));
            existingDevice.setActive(true);
            return deviceRepository.save(existingDevice);
        }

        // Create new device
        DeviceEntity newDevice = new DeviceEntity();
        newDevice.setUser(user);
        newDevice.setDeviceFingerprint(deviceFingerprint);
        newDevice.setIpAddress(getClientIp(request));
        newDevice.setUserAgent(request.getHeader("User-Agent"));
        newDevice.setDeviceName(generateDeviceName(request));
        newDevice.setLastActiveAt(LocalDateTime.now());
        newDevice.setFirstSeenAt(LocalDateTime.now());
        newDevice.setActive(true);
        newDevice.setStreaming(false);

        DeviceEntity savedDevice = deviceRepository.save(newDevice);

        // Check if user exceeded device limit
        checkDeviceLimit(user);

        log.info("✅ New device registered for {}: {}", user.getEmail(), savedDevice.getDeviceName());
        return savedDevice;
    }

    /**
     * Get all active devices for a user
     */
    public List<Map<String, Object>> getUserDevices(User user, HttpServletRequest request) {
        List<DeviceEntity> devices = deviceRepository.findByUserAndIsActiveTrue(user);

        return devices.stream().map(device -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("deviceId", device.getId());
            dto.put("deviceName", device.getDeviceName());
            dto.put("ipAddress", maskIpAddress(device.getIpAddress()));
            dto.put("lastActive", device.getLastActiveAt());
            dto.put("firstSeen", device.getFirstSeenAt());
            dto.put("isStreaming", device.isStreaming());
            dto.put("userAgent", truncateUserAgent(device.getUserAgent()));
            dto.put("isCurrentDevice", isCurrentDevice(device, request));
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * Revoke a specific device
     */
    @Transactional
    public ApiResponse<String> revokeDevice(User user, UUID deviceId) {
        log.info("🔒 Revoking device {} for user: {}", deviceId, user.getEmail());

        Optional<DeviceEntity> deviceOpt = deviceRepository.findById(deviceId);

        if (deviceOpt.isEmpty()) {
            return ApiResponse.error("Device not found");
        }

        DeviceEntity device = deviceOpt.get();

        // Verify device belongs to user
        if (!device.getUser().getId().equals(user.getId())) {
            log.warn("❌ Unauthorized device revocation attempt");
            return ApiResponse.error("Device not found");
        }

        // Cannot revoke current device? (Optional - you can allow or not)
        // You may want to prevent users from revoking their current device

        // Deactivate device
        device.setActive(false);
        deviceRepository.save(device);

        // Revoke all refresh tokens for this device
        refreshTokenRepository.revokeAllDeviceTokens(deviceId, LocalDateTime.now());

        log.info("✅ Device revoked: {}", deviceId);
        return ApiResponse.success("Device revoked successfully");
    }

    /**
     * Handle when user exceeds device limit
     * Returns list of devices user can choose to disconnect
     */
    public ApiResponse<List<Map<String, Object>>> handleDeviceLimit(User user, HttpServletRequest request) {
        long activeDevices = deviceRepository.countByUserAndIsActiveTrue(user);

        if (activeDevices <= maxSessionsPerUser) {
            return ApiResponse.success("Within device limit", null);
        }

        List<DeviceEntity> allDevices = deviceRepository.findByUserAndIsActiveTrue(user);

        // Sort by last active (oldest first)
        allDevices.sort((d1, d2) -> d1.getLastActiveAt().compareTo(d2.getLastActiveAt()));

        // Exclude current device from being disconnected
        Optional<DeviceEntity> currentDeviceOpt = getCurrentDevice(user, request);

        List<Map<String, Object>> disconnectOptions = allDevices.stream()
                .filter(d -> currentDeviceOpt.isEmpty() || !d.getId().equals(currentDeviceOpt.get().getId()))
                .limit(activeDevices - maxSessionsPerUser + 1) // Show options to reduce to limit
                .map(device -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("deviceId", device.getId());
                    dto.put("deviceName", device.getDeviceName());
                    dto.put("lastActive", device.getLastActiveAt());
                    dto.put("ipAddress", maskIpAddress(device.getIpAddress()));
                    return dto;
                })
                .collect(Collectors.toList());

        return ApiResponse.success(
                String.format("You have %d active devices. Maximum allowed is %d.",
                        activeDevices, maxSessionsPerUser),
                disconnectOptions
        );
    }

    /**
     * User chooses which device to disconnect
     */
    @Transactional
    public ApiResponse<String> disconnectDevice(User user, UUID deviceId) {
        log.info("🔌 User {} requesting to disconnect device: {}", user.getEmail(), deviceId);

        // First revoke the device
        ApiResponse<String> revokeResponse = revokeDevice(user, deviceId);

        if (!revokeResponse.isSuccess()) {
            return revokeResponse;
        }

        // Check current active device count
        long activeDevices = deviceRepository.countByUserAndIsActiveTrue(user);

        if (activeDevices <= maxSessionsPerUser) {
            return ApiResponse.success(
                    String.format("Device disconnected. You now have %d active device(s).", activeDevices)
            );
        } else {
            // Still over limit - tell user they need to disconnect more
            return ApiResponse.success(
                    String.format(
                            "Device disconnected. However, you still have %d active devices. Maximum allowed is %d. " +
                                    "Please disconnect another device.",
                            activeDevices, maxSessionsPerUser
                    )
            );
        }
    }

    /**
     * Start streaming on a device
     */
    @Transactional
    public ApiResponse<String> startStreaming(User user, UUID deviceId) {
        log.info("🎥 Starting streaming for device: {}", deviceId);

        Optional<DeviceEntity> deviceOpt = deviceRepository.findById(deviceId);

        if (deviceOpt.isEmpty()) {
            return ApiResponse.error("Device not found");
        }

        DeviceEntity device = deviceOpt.get();

        // Check if device belongs to user
        if (!device.getUser().getId().equals(user.getId())) {
            return ApiResponse.error("Device not found");
        }

        // Check streaming limit
        long streamingCount = deviceRepository.countByUserAndIsStreamingTrue(user);

        if (streamingCount >= maxStreamingSessions) {
            // Find which device is streaming
            List<DeviceEntity> streamingDevices = deviceRepository.findByUserAndIsActiveTrue(user)
                    .stream()
                    .filter(DeviceEntity::isStreaming)
                    .collect(Collectors.toList());

            String streamingOn = streamingDevices.stream()
                    .map(d -> d.getDeviceName() + " (" + maskIpAddress(d.getIpAddress()) + ")")
                    .collect(Collectors.joining(", "));

            return ApiResponse.error(
                    String.format("Streaming already active on: %s. Stop streaming there first.", streamingOn)
            );
        }

        device.setStreaming(true);
        deviceRepository.save(device);

        log.info("✅ Streaming started on device: {}", deviceId);
        return ApiResponse.success("Streaming started");
    }

    /**
     * Stop streaming on a device
     */
    @Transactional
    public ApiResponse<String> stopStreaming(User user, UUID deviceId) {
        Optional<DeviceEntity> deviceOpt = deviceRepository.findById(deviceId);

        if (deviceOpt.isEmpty()) {
            return ApiResponse.error("Device not found");
        }

        DeviceEntity device = deviceOpt.get();

        if (!device.getUser().getId().equals(user.getId())) {
            return ApiResponse.error("Device not found");
        }

        device.setStreaming(false);
        deviceRepository.save(device);

        log.info("⏹️ Streaming stopped on device: {}", deviceId);
        return ApiResponse.success("Streaming stopped");
    }

    /**
     * Clean up inactive devices
     */
    @Transactional
    public void cleanupInactiveDevices() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30); // 30 days inactivity

        List<DeviceEntity> allDevices = deviceRepository.findAll();
        List<DeviceEntity> inactiveDevices = allDevices.stream()
                .filter(d -> d.getLastActiveAt().isBefore(cutoff))
                .collect(Collectors.toList());

        for (DeviceEntity device : inactiveDevices) {
            device.setActive(false);
            refreshTokenRepository.revokeAllDeviceTokens(device.getId(), LocalDateTime.now());
        }

        deviceRepository.saveAll(inactiveDevices);
        log.info("🧹 Cleaned up {} inactive devices", inactiveDevices.size());
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Get current device for user based on request
     */
    public Optional<DeviceEntity> getCurrentDevice(User user, HttpServletRequest request) {
        String currentFingerprint = generateDeviceFingerprint(request);
        return deviceRepository.findByUserAndDeviceFingerprint(user, currentFingerprint);
    }

    /**
     * Check if device is the current device
     */
    private boolean isCurrentDevice(DeviceEntity device, HttpServletRequest request) {
        String currentFingerprint = generateDeviceFingerprint(request);
        return device.getDeviceFingerprint().equals(currentFingerprint);
    }

    /**
     * Get client IP address from request
     */
    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Generate device name from user agent
     */
    private String generateDeviceName(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");

        if (userAgent == null) return "Unknown Device";

        // Detect browser first
        String browser = "Unknown Browser";
        if (userAgent.contains("Chrome") && !userAgent.contains("Edg")) {
            browser = "Chrome";
        } else if (userAgent.contains("Edg")) {
            browser = "Edge";
        } else if (userAgent.contains("Firefox")) {
            browser = "Firefox";
        } else if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) {
            browser = "Safari";
        } else if (userAgent.contains("Opera") || userAgent.contains("OPR")) {
            browser = "Opera";
        }

        // Detect OS
        String os = "Unknown OS";
        if (userAgent.contains("Windows NT 10.0")) {
            os = "Windows 10";
        } else if (userAgent.contains("Windows NT 11.0")) {
            os = "Windows 11";
        } else if (userAgent.contains("Mac OS X")) {
            os = "macOS";
        } else if (userAgent.contains("Linux")) {
            os = "Linux";
        } else if (userAgent.contains("Android")) {
            os = "Android";
        } else if (userAgent.contains("iPhone") || userAgent.contains("iPad")) {
            os = "iOS";
        }

        return browser + " on " + os;
    }

    /**
     * Mask IP address for logging
     */
    private String maskIpAddress(String ip) {
        if (ip == null) return "unknown";
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".***";
        }
        return "***.***.***.***";
    }

    /**
     * Truncate user agent for display
     */
    private String truncateUserAgent(String ua) {
        if (ua == null) return "";
        if (ua.length() <= 50) return ua;
        return ua.substring(0, 47) + "...";
    }

    /**
     * Check if user exceeds device limit
     */
    private void checkDeviceLimit(User user) {
        long activeDevices = deviceRepository.countByUserAndIsActiveTrue(user);
        if (activeDevices > maxSessionsPerUser) {
            log.warn("⚠️ User {} has {} devices (limit: {})",
                    user.getEmail(), activeDevices, maxSessionsPerUser);
        }
    }
}