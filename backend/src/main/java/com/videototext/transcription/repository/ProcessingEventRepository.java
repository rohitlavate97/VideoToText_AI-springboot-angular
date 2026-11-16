package com.videototext.transcription.repository;

import com.videototext.transcription.entity.ProcessingEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProcessingEventRepository extends JpaRepository<ProcessingEventRecord, UUID> {

    List<ProcessingEventRecord> findByJobIdOrderByCreatedAtAsc(UUID jobId);
}
