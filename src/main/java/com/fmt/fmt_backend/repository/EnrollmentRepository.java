package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Batch;
import com.fmt.fmt_backend.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    Optional<Enrollment> findByStudentIdAndBatchId(UUID studentId, UUID batchId);

    List<Enrollment> findByStudentId(UUID studentId);

    List<Enrollment> findByBatchId(UUID batchId);

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.batch.id = :batchId AND e.status = 'ACTIVE'")
    long countActiveEnrollmentsByBatchId(@Param("batchId") UUID batchId);

    boolean existsByStudentIdAndBatchId(UUID studentId, UUID batchId);

    @Query("SELECT e.batch FROM Enrollment e WHERE e.student.id = :studentId AND e.status = 'ACTIVE'")
    List<Batch> findActiveBatchesByStudentId(@Param("studentId") UUID studentId);
}