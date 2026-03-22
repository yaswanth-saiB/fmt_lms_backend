package com.fmt.fmt_backend.service;

import com.fmt.fmt_backend.dto.*;
import com.fmt.fmt_backend.entity.User;
import com.fmt.fmt_backend.enums.UserRole;
import com.fmt.fmt_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final BatchRepository batchRepository;
    private final MeetingRepository meetingRepository;
    private final RecordingRepository recordingRepository;
    private final BatchService batchService;
    private final MeetingService meetingService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final DeviceRepository deviceRepository;
    private final BatchEnrollmentRepository batchEnrollmentRepository;
    private final OtpRepository otpRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    // ---------------------------------------------------------------
    // Dashboard
    // ---------------------------------------------------------------

    public AdminDashboardResponse getDashboard() {
        long totalUsers     = userRepository.count();
        long totalStudents  = userRepository.countByUserRole(UserRole.STUDENT);
        long totalMentors   = userRepository.countByUserRole(UserRole.MENTOR);
        long totalCourses   = courseRepository.count();
        long totalBatches   = batchRepository.count();
        long totalClasses   = meetingRepository.count();
        long totalRecordings = recordingRepository.count();

        List<MeetingResponse> upcomingClasses = meetingRepository
                .findAllUpcoming(PageRequest.of(0, 5))
                .stream()
                .map(meetingService::toAdminResponse)
                .collect(Collectors.toList());

        List<UserResponse> recentUsers = userRepository
                .findRecentUsers(PageRequest.of(0, 5))
                .stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());

        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .totalStudents(totalStudents)
                .totalMentors(totalMentors)
                .totalCourses(totalCourses)
                .totalBatches(totalBatches)
                .totalClasses(totalClasses)
                .totalRecordings(totalRecordings)
                .upcomingClasses(upcomingClasses)
                .recentUsers(recentUsers)
                .build();
    }

    // ---------------------------------------------------------------
    // Users
    // ---------------------------------------------------------------

    public List<UserResponse> getAllUsers(UserRole role) {
        List<User> users = (role != null)
                ? userRepository.findAllByUserRoleOrderByCreatedAtDesc(role)
                : userRepository.findAllByOrderByCreatedAtDesc();
        return users.stream().map(this::toUserResponse).collect(Collectors.toList());
    }

    public UserResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return toUserResponse(user);
    }

    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already registered");
        }

        UserRole role = request.getRole() != null ? request.getRole() : UserRole.STUDENT;

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .gender(request.getGender())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .postalCode(request.getPostalCode())
                .userRole(role)
                .isActive(true)
                .isEmailVerified(true)
                .emailVerifiedAt(LocalDateTime.now())
                .isMobileVerified(true)
                .mobileVerifiedAt(LocalDateTime.now())
                .failedLoginAttempts(0)
                .lastPasswordChangeAt(LocalDateTime.now())
                .build();

        User saved = userRepository.save(user);
        log.info("Admin created user: {} with role {}", saved.getEmail(), role);
        return toUserResponse(saved);
    }

    public UserResponse updateRole(UUID userId, UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserRole oldRole = user.getUserRole();
        user.setUserRole(newRole);
        User saved = userRepository.save(user);
        log.info("Admin updated role: {} {} -> {}", saved.getEmail(), oldRole, newRole);
        return toUserResponse(saved);
    }

    public UserResponse updateStatus(UUID userId, Boolean isActive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setIsActive(isActive);
        User saved = userRepository.save(user);
        log.info("Admin updated status: {} isActive={}", saved.getEmail(), isActive);
        return toUserResponse(saved);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Block deletion of MENTORs who still own courses
        if (user.getUserRole() == UserRole.MENTOR && courseRepository.countByMentor(user) > 0) {
            throw new RuntimeException(
                "Cannot delete mentor with existing courses. " +
                "Delete or reassign their courses first."
            );
        }

        // 1. Revoke & delete refresh tokens (FK → user + device — must go first)
        refreshTokenRepository.deleteByUser(user);

        // 2. Delete sessions (FK → user + device — must go before devices)
        userSessionRepository.deleteByUser(user);

        // 3. Delete devices (FK → user)
        deviceRepository.deleteByUser(user);

        // 4. Delete batch enrollments (FK → user as student)
        batchEnrollmentRepository.deleteByStudent(user);

        // 5. Delete OTPs (plain userId column — no FK)
        otpRepository.deleteByUserId(user.getId());

        // 6. Delete email verification tokens (FK → user)
        emailVerificationTokenRepository.deleteByUserId(user.getId());

        // 7. Finally delete the user
        userRepository.delete(user);

        log.info("Admin deleted user: {} (role={})", user.getEmail(), user.getUserRole());
    }

    public void resetPassword(UUID userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setLastPasswordChangeAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setAccountLockedUntil(null);
        userRepository.save(user);
        log.info("Admin reset password for user: {}", user.getEmail());
    }

    // ---------------------------------------------------------------
    // Courses / Batches / Classes / Recordings
    // ---------------------------------------------------------------

    public List<CourseResponse> getAllCourses() {
        return courseRepository.findAll()
                .stream()
                .map(c -> CourseResponse.builder()
                        .id(c.getId())
                        .title(c.getTitle())
                        .description(c.getDescription())
                        .price(c.getPrice())
                        .isActive(c.getIsActive())
                        .mentorName(c.getMentor().getFirstName() + " " + c.getMentor().getLastName())
                        .createdAt(c.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public List<BatchResponse> getAllBatches() {
        return batchRepository.findAll()
                .stream()
                .map(batchService::toResponse)
                .collect(Collectors.toList());
    }

    public List<MeetingResponse> getAllClasses() {
        return meetingRepository.findAll()
                .stream()
                .map(meetingService::toAdminResponse)
                .collect(Collectors.toList());
    }

    public List<RecordingResponse> getAllRecordings() {
        return recordingRepository.findAll()
                .stream()
                .map(r -> RecordingResponse.builder()
                        .id(r.getId())
                        .title(r.getTitle())
                        .batchId(r.getBatch().getId())
                        .batchName(r.getBatch().getName())
                        .status(r.getStatus())
                        .durationMins(r.getDurationMins())
                        .createdAt(r.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    public UserResponse toUserResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .phoneNumber(u.getPhoneNumber())
                .role(u.getUserRole())
                .gender(u.getGender())
                .city(u.getCity())
                .state(u.getState())
                .country(u.getCountry())
                .postalCode(u.getPostalCode())
                .isActive(u.getIsActive())
                .isEmailVerified(u.getIsEmailVerified())
                .isMobileVerified(u.getIsMobileVerified())
                .lastLoginAt(u.getLastLoginAt())
                .createdAt(u.getCreatedAt())
                .failedLoginAttempts(u.getFailedLoginAttempts())
                .build();
    }
}
