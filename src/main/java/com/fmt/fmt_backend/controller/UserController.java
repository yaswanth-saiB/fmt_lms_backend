package com.fmt.fmt_backend.controller;

import com.fmt.fmt_backend.dto.ApiResponse;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final AuthService authService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProfile() {
        Optional<User> currentUser = authService.getCurrentUser();

        if (currentUser.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Not authenticated"));
        }

        User user = currentUser.get();

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("email", user.getEmail());
        profile.put("firstName", user.getFirstName());
        profile.put("lastName", user.getLastName());
        profile.put("userRole", user.getUserRole());
        profile.put("gender", user.getGender());
        profile.put("phoneNumber", user.getPhoneNumber());
        profile.put("city", user.getCity());
        profile.put("state", user.getState());
        profile.put("country", user.getCountry());
        profile.put("postalCode", user.getPostalCode());
        profile.put("isEmailVerified", user.getIsEmailVerified());
        profile.put("isMobileVerified", user.getIsMobileVerified());
        profile.put("lastLoginAt", user.getLastLoginAt());
        profile.put("createdAt", user.getCreatedAt());

        log.info("📊 Profile accessed for: {}", user.getEmail());

        return ResponseEntity.ok(ApiResponse.success("Profile retrieved", profile));
    }
}