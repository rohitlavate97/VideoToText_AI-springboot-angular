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
import { VideoService } from '../../../core/services/video.service';
import { TranscriptionService } from '../../../core/services/transcription.service';
import { SpeakerService } from '../../../core/services/speaker.service';
import { VideoResponse } from '../../../core/models/video.model';
import { SpeakerResponse } from '../../../core/models/speaker.model';
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
    MatSelectModule
  ],
  templateUrl: './video-detail.component.html',
  styleUrls: ['./video-detail.component.scss']
})
export class VideoDetailComponent implements OnInit, OnDestroy {
  @ViewChild('videoElement') videoRef?: ElementRef<HTMLVideoElement>;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly videoService = inject(VideoService);
  private readonly transcriptionService = inject(TranscriptionService);
  private readonly speakerService = inject(SpeakerService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  video: VideoResponse | null = null;
  activeJob: TranscriptionJobResponse | null = null;
  segments: TranscriptSegmentResponse[] = [];
  speakers: SpeakerResponse[] = [];
  progressEvent: JobProgressEvent | null = null;
  searchQuery = '';
  selectedSpeakerFilter: string | null = null;

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
      this.videoRef.nativeElement.play().catch(() => {});
    }
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
