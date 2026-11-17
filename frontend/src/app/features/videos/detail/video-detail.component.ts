import { Component, OnInit, OnDestroy, ViewChild, ElementRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { VideoService } from '../../../core/services/video.service';
import { TranscriptionService } from '../../../core/services/transcription.service';
import { SpeakerService } from '../../../core/services/speaker.service';
import { TranslationService } from '../../../core/services/translation.service';
import { SummaryService } from '../../../core/services/summary.service';
import { SubtitleService } from '../../../core/services/subtitle.service';
import { VideoResponse } from '../../../core/models/video.model';
import { SpeakerResponse } from '../../../core/models/speaker.model';
import { TranslationResponse } from '../../../core/models/translation.model';
import { SummaryResponse, ActionItemDto, ChapterDto, TopicDto } from '../../../core/models/summary.model';
import { SubtitleResponse } from '../../../core/models/subtitle.model';
import {
  CreateTranscriptionRequest,
  JobProgressEvent,
  TranscriptRevision,
  TranscriptSegmentResponse,
  TranscriptionJobResponse
} from '../../../core/models/transcription.model';
import {
  TranscriptionDialogComponent,
  TranscriptionDialogData
} from '../../transcriptions/dialog/transcription-dialog.component';

@Component({
  selector: 'app-video-detail',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatProgressBarModule,
    MatSnackBarModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatMenuModule,
    MatCheckboxModule
  ],
  templateUrl: './video-detail.component.html',
  styleUrls: ['./video-detail.component.scss']
})
export class VideoDetailComponent implements OnInit, OnDestroy {
  @ViewChild('videoElement') videoRef?: ElementRef<HTMLVideoElement>;
  @ViewChild('waveformCanvas') waveformRef?: ElementRef<HTMLCanvasElement>;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly videoService = inject(VideoService);
  private readonly transcriptionService = inject(TranscriptionService);
  private readonly speakerService = inject(SpeakerService);
  private readonly translationService = inject(TranslationService);
  private readonly summaryService = inject(SummaryService);
  private readonly subtitleService = inject(SubtitleService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  video: VideoResponse | null = null;
  activeJob: TranscriptionJobResponse | null = null;
  segments: TranscriptSegmentResponse[] = [];
  speakers: SpeakerResponse[] = [];
  translations: TranslationResponse[] = [];
  selectedLanguage: string = 'original';
  isTranslating: boolean = false;
  progressEvent: JobProgressEvent | null = null;
  searchQuery = '';
  selectedSpeakerFilter: string | null = null;

  // Playback & Waveform State
  currentTime = 0;
  duration = 0;
  isPlaying = false;
  playbackSpeed = 1.0;

  // Subtitles & Exports State
  subtitles: SubtitleResponse[] = [];
  isLoadingSubtitles = false;
  isExporting = false;
  selectedExportFormat: string = 'SRT';
  exportIncludeSpeakers: boolean = false;

  // Summary & Intelligent Insights State
  summary: SummaryResponse | null = null;
  isLoadingSummary = false;
  isGeneratingSummary = false;
  selectedSummaryLength: string = 'medium';
  selectedSummaryFormat: string = 'paragraph';
  activeInsightTab: 'summary' | 'key_points' | 'action_items' | 'topics' | 'chapters' = 'summary';

  // Segment Editing State
  editingSegmentId: string | null = null;
  editText = '';
  editStartTime = 0;
  editEndTime = 0;
  editSpeakerId: string | null = null;
  isSavingSegment = false;

  // Revisions Modal State
  showRevisionsModal = false;
  revisions: TranscriptRevision[] = [];
  isLoadingRevisions = false;

  isLoading = true;
  isLoadingTranscript = false;
  errorMessage: string | null = null;
  private sseSub?: Subscription;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadVideo(id);
    } else {
      this.errorMessage = 'Invalid video ID';
      this.isLoading = false;
    }
  }

  ngOnDestroy(): void {
    this.sseSub?.unsubscribe();
  }

  loadVideo(id: string): void {
    this.isLoading = true;
    this.videoService.getVideo(id).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.video = res.data;
          this.loadVideoJobs(id);
        } else {
          this.errorMessage = res.message || 'Video not found';
          this.isLoading = false;
        }
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.error?.message || 'Failed to load video details';
      }
    });
  }

  loadVideoJobs(videoId: string): void {
    this.transcriptionService.getVideoJobs(videoId).subscribe({
      next: (res) => {
        this.isLoading = false;
        if (res.success && res.data && res.data.length > 0) {
          this.activeJob = res.data[0];
          if (this.activeJob.status === 'PROCESSING' || this.activeJob.status === 'QUEUED') {
            this.listenToProgress(this.activeJob.id);
          } else if (this.activeJob.status === 'COMPLETED') {
            this.loadSegments(this.activeJob.id);
            this.loadSpeakers(this.activeJob.id);
            this.loadTranslations(this.activeJob.id);
            this.loadSummary(this.activeJob.id);
            this.loadSubtitles(this.activeJob.id);
          }
        }
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  listenToProgress(jobId: string): void {
    this.sseSub?.unsubscribe();
    this.sseSub = this.transcriptionService.subscribeToJobProgress(jobId).subscribe({
      next: (evt) => {
        this.progressEvent = evt;
        if (this.activeJob) {
          this.activeJob.currentStage = evt.stage;
          this.activeJob.progressPercent = evt.progressPercent;
          this.activeJob.completedChunks = evt.completedChunks;
          this.activeJob.totalChunks = evt.totalChunks;
          if (evt.stage === 'COMPLETED') {
            this.activeJob.status = 'COMPLETED';
            this.loadSegments(jobId);
            this.loadSpeakers(jobId);
            this.loadTranslations(jobId);
            this.loadSummary(jobId);
            this.loadSubtitles(jobId);
          }
        }
      },
      error: (err) => {
        console.warn('SSE subscription closed:', err);
      }
    });
  }

  loadSegments(jobId: string): void {
    this.isLoadingTranscript = true;
    this.transcriptionService.getJobSegments(jobId).subscribe({
      next: (res) => {
        this.isLoadingTranscript = false;
        if (res.success && res.data) {
          this.segments = res.data;
        }
      },
      error: () => {
        this.isLoadingTranscript = false;
      }
    });
  }

  loadSpeakers(jobId: string): void {
    this.speakerService.getSpeakers(jobId).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.speakers = res.data;
        }
      },
      error: () => {}
    });
  }

  renameSpeaker(speaker: SpeakerResponse): void {
    const currentName = speaker.displayName || speaker.speakerLabel;
    const newName = prompt(`Rename ${currentName}:`, currentName);
    if (newName && newName.trim() && newName.trim() !== currentName) {
      this.speakerService.updateSpeaker(speaker.jobId, speaker.id, { displayName: newName.trim() }).subscribe({
        next: (res) => {
          if (res.success && res.data) {
            speaker.displayName = res.data.displayName;
            this.snackBar.open(`Renamed to "${res.data.displayName}"`, 'OK', { duration: 3000 });
          }
        },
        error: () => {
          this.snackBar.open('Failed to update speaker name', 'Close', { duration: 3000 });
        }
      });
    }
  }

  filterBySpeaker(label: string | null): void {
    this.selectedSpeakerFilter = this.selectedSpeakerFilter === label ? null : label;
  }

  loadTranslations(jobId: string): void {
    this.translationService.getTranslations(jobId).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.translations = res.data;
        }
      },
      error: () => {}
    });
  }

  selectLanguage(lang: string): void {
    this.selectedLanguage = lang;
  }

  requestNewTranslation(lang: string): void {
    if (!this.activeJob) return;
    this.isTranslating = true;
    this.translationService.requestTranslation(this.activeJob.id, { targetLanguage: lang }).subscribe({
      next: (res) => {
        this.isTranslating = false;
        if (res.success && res.data) {
          this.translations.push(res.data);
          this.selectedLanguage = lang;
          this.snackBar.open(`Translated transcript into ${lang.toUpperCase()} successfully!`, 'OK', { duration: 3000 });
        }
      },
      error: (err) => {
        this.isTranslating = false;
        const msg = err?.error?.error?.message || 'Failed to generate translation';
        this.snackBar.open(msg, 'Close', { duration: 4000 });
      }
    });
  }

  hasTranslation(lang: string): boolean {
    return this.translations.some(t => t.targetLanguage.toLowerCase() === lang.toLowerCase());
  }

  loadSummary(jobId: string): void {
    this.isLoadingSummary = true;
    this.summaryService.getSummary(jobId).subscribe({
      next: (res) => {
        this.isLoadingSummary = false;
        if (res.success && res.data) {
          this.summary = res.data;
        }
      },
      error: () => {
        this.isLoadingSummary = false;
      }
    });
  }

  generateSummary(length?: string, format?: string): void {
    if (!this.activeJob) return;
    this.isGeneratingSummary = true;
    const reqLen = length || this.selectedSummaryLength;
    const reqFormat = format || this.selectedSummaryFormat;

    this.summaryService.generateSummary(this.activeJob.id, {
      summaryLength: reqLen,
      summaryFormat: reqFormat,
      extractKeyPoints: true,
      extractActionItems: true,
      extractTopics: true,
      generateChapters: true
    }).subscribe({
      next: (res) => {
        this.isGeneratingSummary = false;
        if (res.success && res.data) {
          this.summary = res.data;
          this.snackBar.open('AI Summary & Insights generated successfully!', 'OK', { duration: 3000 });
        }
      },
      error: (err) => {
        this.isGeneratingSummary = false;
        const msg = err?.error?.error?.message || 'Failed to generate summary';
        this.snackBar.open(msg, 'Close', { duration: 4000 });
      }
    });
  }

  deleteSummary(): void {
    if (!this.activeJob) return;
    if (confirm('Are you sure you want to delete this summary?')) {
      this.summaryService.deleteSummary(this.activeJob.id).subscribe({
        next: () => {
          this.summary = null;
          this.snackBar.open('Summary deleted', 'OK', { duration: 3000 });
        },
        error: () => {
          this.snackBar.open('Failed to delete summary', 'Close', { duration: 3000 });
        }
      });
    }
  }

  toggleActionItem(item: ActionItemDto): void {
    item.completed = !item.completed;
  }

  switchInsightTab(tab: 'summary' | 'key_points' | 'action_items' | 'topics' | 'chapters'): void {
    this.activeInsightTab = tab;
  }

  getSegmentDisplayText(seg: TranscriptSegmentResponse): string {
    if (this.selectedLanguage === 'original') {
      return seg.text;
    }
    const tr = this.translations.find(t => t.targetLanguage.toLowerCase() === this.selectedLanguage.toLowerCase());
    if (tr && tr.segments) {
      const match = tr.segments.find(s => s.segmentIndex === seg.segmentIndex);
      if (match && match.translatedText) {
        return match.translatedText;
      }
    }
    return seg.text;
  }

  getSpeakerName(label?: string): string {
    if (!label) return 'Unknown Speaker';
    const found = this.speakers.find(s => s.speakerLabel === label);
    return found ? found.displayName : label;
  }

  startEditingSegment(seg: TranscriptSegmentResponse, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.editingSegmentId = seg.id;
    this.editText = seg.text;
    this.editStartTime = Number(seg.startTime.toFixed(2));
    this.editEndTime = Number(seg.endTime.toFixed(2));
    this.editSpeakerId = seg.speakerId || null;
  }

  cancelEditingSegment(event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.editingSegmentId = null;
  }

  saveSegment(seg: TranscriptSegmentResponse, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    if (!this.activeJob) return;

    if (this.editStartTime < 0 || this.editEndTime <= this.editStartTime) {
      this.snackBar.open('Invalid timestamps: start time must be non-negative and less than end time', 'Close', { duration: 3500 });
      return;
    }

    if (!this.editText.trim()) {
      this.snackBar.open('Transcript text cannot be empty', 'Close', { duration: 3000 });
      return;
    }

    this.isSavingSegment = true;
    this.transcriptionService.updateSegment(this.activeJob.id, seg.id, {
      text: this.editText.trim(),
      startTime: this.editStartTime,
      endTime: this.editEndTime,
      speakerId: this.editSpeakerId || undefined
    }).subscribe({
      next: (res) => {
        this.isSavingSegment = false;
        if (res.success && res.data) {
          seg.text = res.data.text;
          seg.startTime = res.data.startTime;
          seg.endTime = res.data.endTime;
          seg.speakerId = res.data.speakerId;
          seg.speakerLabel = res.data.speakerLabel;
          seg.speakerDisplayName = res.data.speakerDisplayName;
          this.editingSegmentId = null;
          this.snackBar.open('Segment updated successfully', 'OK', { duration: 3000 });
        }
      },
      error: (err) => {
        this.isSavingSegment = false;
        const msg = err?.error?.error?.message || 'Failed to update segment';
        this.snackBar.open(msg, 'Close', { duration: 4000 });
      }
    });
  }

  openRevisionsModal(): void {
    if (!this.activeJob) return;
    this.showRevisionsModal = true;
    this.isLoadingRevisions = true;
    this.transcriptionService.getRevisions(this.activeJob.id).subscribe({
      next: (res) => {
        this.isLoadingRevisions = false;
        if (res.success && res.data) {
          this.revisions = res.data;
        }
      },
      error: () => {
        this.isLoadingRevisions = false;
        this.snackBar.open('Failed to load revision history', 'Close', { duration: 3000 });
      }
    });
  }

  closeRevisionsModal(): void {
    this.showRevisionsModal = false;
  }

  openTranscriptionDialog(): void {
    if (!this.video) return;

    const dialogRef = this.dialog.open(TranscriptionDialogComponent, {
      width: '560px',
      data: {
        videoId: this.video.id,
        videoTitle: this.video.originalFilename
      } as TranscriptionDialogData
    });

    dialogRef.afterClosed().subscribe((req?: CreateTranscriptionRequest) => {
      if (req) {
        this.startTranscription(req);
      }
    });
  }

  startTranscription(req: CreateTranscriptionRequest): void {
    this.transcriptionService.createJob(req).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.activeJob = res.data;
          this.segments = [];
          this.speakers = [];
          this.selectedSpeakerFilter = null;
          this.snackBar.open('Transcription job queued successfully!', 'OK', { duration: 3000 });
          this.listenToProgress(this.activeJob.id);
        }
      },
      error: (err) => {
        const msg = err?.error?.error?.message || 'Failed to submit transcription job';
        this.snackBar.open(msg, 'Close', { duration: 4000 });
      }
    });
  }

  cancelJob(): void {
    if (!this.activeJob) return;

    if (confirm('Are you sure you want to cancel the active transcription job?')) {
      this.transcriptionService.cancelJob(this.activeJob.id).subscribe({
        next: (res) => {
          if (res.success && res.data) {
            this.activeJob = res.data;
            this.sseSub?.unsubscribe();
            this.snackBar.open('Job cancelled', 'OK', { duration: 3000 });
          }
        },
        error: () => {
          this.snackBar.open('Failed to cancel job', 'Close', { duration: 3000 });
        }
      });
    }
  }

  seekTo(seconds: number): void {
    if (this.videoRef?.nativeElement) {
      this.videoRef.nativeElement.currentTime = seconds;
      this.currentTime = seconds;
      this.videoRef.nativeElement.play().catch(() => {});
      this.isPlaying = true;
      this.drawWaveform();
    }
  }

  togglePlayPause(): void {
    if (!this.videoRef?.nativeElement) return;
    const v = this.videoRef.nativeElement;
    if (v.paused) {
      v.play().catch(() => {});
      this.isPlaying = true;
    } else {
      v.pause();
      this.isPlaying = false;
    }
  }

  skipTime(deltaSeconds: number): void {
    if (!this.videoRef?.nativeElement) return;
    const v = this.videoRef.nativeElement;
    const total = this.duration || v.duration || (this.video?.durationSeconds || 0);
    const target = Math.max(0, Math.min(total, v.currentTime + deltaSeconds));
    v.currentTime = target;
    this.currentTime = target;
    this.drawWaveform();
  }

  setPlaybackSpeed(speed: number): void {
    if (!this.videoRef?.nativeElement) return;
    this.playbackSpeed = speed;
    this.videoRef.nativeElement.playbackRate = speed;
  }

  onVideoTimeUpdate(): void {
    if (this.videoRef?.nativeElement) {
      this.currentTime = this.videoRef.nativeElement.currentTime;
      this.isPlaying = !this.videoRef.nativeElement.paused;
      this.drawWaveform();
    }
  }

  onVideoLoadedMetadata(): void {
    if (this.videoRef?.nativeElement) {
      this.duration = this.videoRef.nativeElement.duration || (this.video?.durationSeconds || 0);
      this.drawWaveform();
    }
  }

  onWaveformClick(event: MouseEvent): void {
    const canvas = this.waveformRef?.nativeElement;
    const total = this.duration || (this.video?.durationSeconds || 0);
    if (!canvas || total <= 0) return;
    const rect = canvas.getBoundingClientRect();
    const clickX = event.clientX - rect.left;
    const ratio = Math.max(0, Math.min(1, clickX / rect.width));
    const targetSeconds = ratio * total;
    this.seekTo(targetSeconds);
  }

  drawWaveform(): void {
    const canvas = this.waveformRef?.nativeElement;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const width = canvas.width = canvas.parentElement?.clientWidth || 500;
    const height = canvas.height = 48;
    ctx.clearRect(0, 0, width, height);

    const totalBars = Math.floor(width / 4);
    const totalDuration = this.duration || (this.video?.durationSeconds || 120);

    for (let i = 0; i < totalBars; i++) {
      const barTime = (i / totalBars) * totalDuration;
      const hasSpeech = this.segments.some(s => barTime >= s.startTime && barTime <= s.endTime);

      const seed = Math.sin(i * 12.9898) * 43758.5453;
      const pseudoRand = seed - Math.floor(seed);
      const amp = hasSpeech ? 0.35 + pseudoRand * 0.55 : 0.1 + pseudoRand * 0.12;

      const barHeight = Math.max(4, height * amp);
      const x = i * 4;
      const y = (height - barHeight) / 2;

      if (barTime <= this.currentTime) {
        ctx.fillStyle = hasSpeech ? '#4f46e5' : '#818cf8';
      } else {
        ctx.fillStyle = hasSpeech ? '#cbd5e1' : '#f1f5f9';
      }

      ctx.beginPath();
      ctx.roundRect(x, y, 2.5, barHeight, 1.5);
      ctx.fill();
    }
  }

  isSegmentActive(seg: TranscriptSegmentResponse): boolean {
    return this.currentTime >= seg.startTime && this.currentTime <= seg.endTime;
  }

  get filteredSegments(): TranscriptSegmentResponse[] {
    let result = this.segments;
    if (this.selectedSpeakerFilter) {
      result = result.filter(s => s.speakerLabel === this.selectedSpeakerFilter);
    }
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      result = result.filter(s => s.text.toLowerCase().includes(q));
    }
    return result;
  }

  loadSubtitles(jobId: string): void {
    this.isLoadingSubtitles = true;
    this.subtitleService.getSubtitles(jobId).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.subtitles = res.data;
        }
        this.isLoadingSubtitles = false;
      },
      error: () => {
        this.isLoadingSubtitles = false;
      }
    });
  }

  quickExport(format: string): void {
    if (!this.activeJob) return;
    this.isExporting = true;
    const lang = this.selectedLanguage === 'original'
      ? (this.activeJob.language || 'en')
      : this.selectedLanguage;

    this.subtitleService.downloadSubtitle(this.activeJob.id, format, lang, this.exportIncludeSpeakers).subscribe({
      next: (blob) => {
        const ext = format.toLowerCase();
        const filename = `${this.video?.originalFilename || 'transcript'}_${lang}.${ext}`;
        this.subtitleService.saveBlob(blob, filename);
        this.isExporting = false;
        this.snackBar.open(`Exported ${format} successfully`, 'Close', { duration: 3000 });
        if (this.activeJob) {
          this.loadSubtitles(this.activeJob.id);
        }
      },
      error: (err) => {
        this.isExporting = false;
        this.snackBar.open('Export failed: ' + (err.error?.message || err.message), 'Close', { duration: 4000 });
      }
    });
  }

  generateAndDownloadSubtitle(): void {
    if (!this.activeJob) return;
    this.isExporting = true;
    const lang = this.selectedLanguage === 'original'
      ? (this.activeJob.language || 'en')
      : this.selectedLanguage;

    this.subtitleService.generateSubtitle(this.activeJob.id, {
      format: this.selectedExportFormat,
      language: lang,
      includeSpeakers: this.exportIncludeSpeakers
    }).subscribe({
      next: () => {
        this.isExporting = false;
        this.snackBar.open(`Generated ${this.selectedExportFormat} subtitle`, 'Close', { duration: 3000 });
        if (this.activeJob) {
          this.loadSubtitles(this.activeJob.id);
        }
        this.quickExport(this.selectedExportFormat);
      },
      error: (err) => {
        this.isExporting = false;
        this.snackBar.open('Failed to generate subtitle: ' + (err.error?.message || err.message), 'Close', { duration: 4000 });
      }
    });
  }

  downloadExistingSubtitle(sub: SubtitleResponse): void {
    if (!this.activeJob) return;
    this.subtitleService.downloadSubtitleById(this.activeJob.id, sub.id).subscribe({
      next: (blob) => {
        const ext = sub.format.toLowerCase();
        const filename = `${this.video?.originalFilename || 'transcript'}_${sub.language}.${ext}`;
        this.subtitleService.saveBlob(blob, filename);
        this.snackBar.open(`Downloaded ${sub.format} subtitle`, 'Close', { duration: 3000 });
      },
      error: () => {
        this.snackBar.open('Download failed', 'Close', { duration: 3000 });
      }
    });
  }

  deleteExistingSubtitle(sub: SubtitleResponse): void {
    if (!this.activeJob) return;
    this.subtitleService.deleteSubtitle(this.activeJob.id, sub.id).subscribe({
      next: () => {
        this.subtitles = this.subtitles.filter(s => s.id !== sub.id);
        this.snackBar.open('Subtitle deleted', 'Close', { duration: 3000 });
      },
      error: () => {
        this.snackBar.open('Failed to delete subtitle', 'Close', { duration: 3000 });
      }
    });
  }

  deleteVideo(): void {
    if (!this.video) return;

    if (confirm(`Permanently delete "${this.video.originalFilename}" and its cloud storage copy?`)) {
      this.videoService.deleteVideo(this.video.id).subscribe({
        next: () => {
          this.snackBar.open('Video deleted successfully', 'OK', { duration: 3000 });
          this.router.navigate(['/videos']);
        },
        error: () => {
          this.snackBar.open('Failed to delete video', 'Close', { duration: 4000 });
        }
      });
    }
  }

  formatDuration(seconds: number): string {
    return this.videoService.formatDuration(seconds);
  }

  formatFileSize(bytes: number): string {
    return this.videoService.formatFileSize(bytes);
  }

  formatSeconds(totalSeconds: number): string {
    const mins = Math.floor(totalSeconds / 60);
    const secs = Math.floor(totalSeconds % 60);
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }
}
