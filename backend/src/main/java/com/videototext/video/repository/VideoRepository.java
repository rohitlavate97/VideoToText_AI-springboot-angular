package com.videototext.video.repository;

import com.videototext.video.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VideoRepository extends JpaRepository<Video, UUID> {

    Page<Video> findByUserId(UUID userId, Pageable pageable);

    Page<Video> findByUserIdAndStatus(UUID userId, String status, Pageable pageable);

    Optional<Video> findByIdAndUserId(UUID id, UUID userId);
}
