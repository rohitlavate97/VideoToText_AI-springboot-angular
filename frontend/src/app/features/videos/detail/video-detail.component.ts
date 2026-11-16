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
import { VideoService } from '../../../core/services/video.service';
import { TranscriptionService } from '../../../core/services/transcription.service';
import { VideoResponse } from '../../../core/models/video.model';
import {
  CreateTranscriptionRequest,
  JobProgressEvent,
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
    MatInputModule
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
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  video: VideoResponse | null = null;
  activeJob: TranscriptionJobResponse | null = null;
  segments: TranscriptSegmentResponse[] = [];
  progressEvent: JobProgressEvent | null = null;
  searchQuery = '';

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
    if (!this.searchQuery.trim()) {
      return this.segments;
    }
    const q = this.searchQuery.toLowerCase();
    return this.segments.filter(s => s.text.toLowerCase().includes(q));
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
