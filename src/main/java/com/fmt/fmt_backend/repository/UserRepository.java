package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository  // Tells Spring this is a repository component
public interface UserRepository extends JpaRepository<User, UUID> {
    // Spring Data JPA automatically implements these methods!

    // Find user by email
    Optional<User> findByEmail(String email);

    // Check if email exists
    Boolean existsByEmail(String email);

    // Custom query: increment failed attempts
    @Modifying  // This query modifies data
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.email = :email")
    void incrementFailedAttempts(@Param("email") String email);

    // Custom query: reset failed attempts
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.accountLockedUntil = null WHERE u.email = :email")
    void resetFailedAttempts(@Param("email") String email);

    // Custom query: lock account
    @Modifying
    @Query("UPDATE User u SET u.accountLockedUntil = :lockUntil WHERE u.email = :email")
    void lockAccount(@Param("email") String email, @Param("lockUntil") LocalDateTime lockUntil);
}

// SPRING DATA JPA MAGIC:
// findByEmail → SELECT * FROM users WHERE email = ?
// existsByEmail → SELECT COUNT(*) > 0 FROM users WHERE email = ?
// save(user) → INSERT or UPDATE
// findById(id) → SELECT * FROM users WHERE id = ?
// findAll() → SELECT * FROM users
// deleteById(id) → DELETE FROM users WHERE id = ?

// @Query advantages:
// 1. Write custom SQL/HQL
// 2. Better performance
// 3. Complex operations