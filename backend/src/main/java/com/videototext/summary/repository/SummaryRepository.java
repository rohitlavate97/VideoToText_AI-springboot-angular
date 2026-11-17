package com.videototext.summary.repository;

import com.videototext.summary.entity.Summary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SummaryRepository extends JpaRepository<Summary, UUID> {

    @Query("SELECT s FROM Summary s WHERE s.job.id = :jobId")
    Optional<Summary> findByJobId(@Param("jobId") UUID jobId);

    @Query("SELECT COUNT(s) > 0 FROM Summary s WHERE s.job.id = :jobId")
    boolean existsByJobId(@Param("jobId") UUID jobId);

    void deleteByJobId(UUID jobId);
}
