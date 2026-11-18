package com.videototext.video.repository;

import com.videototext.video.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VideoRepository extends JpaRepository<Video, UUID> {

    Page<Video> findByUserId(UUID userId, Pageable pageable);

    List<Video> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserId(UUID userId);

    Page<Video> findByUserIdAndStatus(UUID userId, String status, Pageable pageable);

    Optional<Video> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT COALESCE(SUM(v.fileSizeBytes), 0) FROM Video v WHERE v.user.id = :userId AND v.status <> 'DELETED'")
    long sumFileSizeBytesByUserId(@Param("userId") UUID userId);
}
