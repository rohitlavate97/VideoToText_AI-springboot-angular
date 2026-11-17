package com.videototext.export.repository;

import com.videototext.export.entity.Export;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExportRepository extends JpaRepository<Export, UUID> {

    List<Export> findByJobIdOrderByCreatedAtDesc(UUID jobId);

    List<Export> findByUserIdOrderByCreatedAtDesc(UUID userId);

    void deleteByJobId(UUID jobId);
}
