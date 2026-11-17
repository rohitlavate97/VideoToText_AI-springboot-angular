package com.videototext.dashboard.service;

import com.videototext.dashboard.dto.DashboardStatsResponse;
import com.videototext.dashboard.dto.RecentActivityDto;
import com.videototext.export.repository.ExportRepository;
import com.videototext.transcription.entity.TranscriptSegment;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.TranscriptSegmentRepository;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import com.videototext.video.entity.Video;
import com.videototext.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VideoRepository videoRepository;
    private final TranscriptionJobRepository jobRepository;
    private final TranscriptSegmentRepository segmentRepository;
    private final ExportRepository exportRepository;

    @Transactional(readOnly = true)
    public DashboardStatsResponse getUserStats(UUID userId) {
        List<Video> videos = videoRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<TranscriptionJob> jobs = jobRepository.findByUserIdOrderByCreatedAtDesc(userId);

        long totalVideos = videos.size();
        double totalDuration = videos.stream()
                .mapToDouble(Video::getDurationSeconds)
                .sum();

        // Status counts
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        statusCounts.put("COMPLETED", 0L);
        statusCounts.put("PROCESSING", 0L);
        statusCounts.put("QUEUED", 0L);
        statusCounts.put("FAILED", 0L);

        for (TranscriptionJob job : jobs) {
            String status = job.getStatus() != null ? job.getStatus().toUpperCase(Locale.ROOT) : "QUEUED";
            statusCounts.put(status, statusCounts.getOrDefault(status, 0L) + 1);
        }

        // Language distribution
        Map<String, Long> languageDistribution = new LinkedHashMap<>();
        for (TranscriptionJob job : jobs) {
            String lang = job.getSourceLanguage() != null ? job.getSourceLanguage().toLowerCase(Locale.ROOT) : "en";
            languageDistribution.put(lang, languageDistribution.getOrDefault(lang, 0L) + 1);
        }

        // Total words across completed jobs
        long totalWords = 0;
        List<TranscriptionJob> completedJobs = jobs.stream()
                .filter(j -> "COMPLETED".equalsIgnoreCase(j.getStatus()))
                .collect(Collectors.toList());

        for (TranscriptionJob job : completedJobs) {
            List<TranscriptSegment> segments = segmentRepository.findByJobIdOrderBySegmentIndexAsc(job.getId());
            for (TranscriptSegment seg : segments) {
                if (seg.getText() != null && !seg.getText().isBlank()) {
                    String[] tokens = seg.getText().trim().split("\\s+");
                    totalWords += tokens.length;
                }
            }
        }

        // Total exports
        long totalExports = exportRepository.findByUserIdOrderByCreatedAtDesc(userId).size();

        // Recent activity (latest 5 jobs)
        List<RecentActivityDto> recent = new ArrayList<>();
        int limit = Math.min(5, jobs.size());
        for (int i = 0; i < limit; i++) {
            TranscriptionJob j = jobs.get(i);
            recent.add(RecentActivityDto.builder()
                    .videoId(j.getVideo() != null ? j.getVideo().getId() : null)
                    .jobId(j.getId())
                    .filename(j.getVideo() != null ? j.getVideo().getOriginalFilename() : "Untitled Video")
                    .status(j.getStatus())
                    .durationSeconds(j.getVideo() != null ? j.getVideo().getDurationSeconds() : 0.0)
                    .language(j.getSourceLanguage() != null ? j.getSourceLanguage() : "en")
                    .createdAt(j.getCreatedAt())
                    .build());
        }

        return DashboardStatsResponse.builder()
                .totalVideos(totalVideos)
                .totalAudioDurationSeconds(Math.round(totalDuration * 10.0) / 10.0)
                .totalTranscribedWords(totalWords)
                .totalExports(totalExports)
                .statusCounts(statusCounts)
                .languageDistribution(languageDistribution)
                .recentActivity(recent)
                .build();
    }
}
