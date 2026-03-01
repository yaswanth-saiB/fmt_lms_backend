package com.fmt.fmt_backend.repository;

import com.fmt.fmt_backend.entity.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface BatchRepository extends JpaRepository<Batch, UUID> {

    List<Batch> findByMentorId(UUID mentorId);

    List<Batch> findByCourseId(UUID courseId);

    @Query("SELECT b FROM Batch b JOIN b.enrollments e WHERE e.student.id = :studentId")
    List<Batch> findByStudentId(@Param("studentId") UUID studentId);

    @Query("SELECT b.id FROM Batch b JOIN b.enrollments e WHERE e.student.id = :studentId")
    List<UUID> findBatchIdsByStudentId(@Param("studentId") UUID studentId);

    List<Batch> findByStatusAndStartDateBefore(Batch.BatchStatus status, LocalDate date);

    @Query("SELECT COUNT(b) > 0 FROM Batch b JOIN b.enrollments e WHERE b.id = :batchId AND e.student.id = :studentId")
    boolean isStudentEnrolled(@Param("batchId") UUID batchId, @Param("studentId") UUID studentId);
}
