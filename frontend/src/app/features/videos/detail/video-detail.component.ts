import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { VideoService } from '../../../core/services/video.service';
import { VideoResponse } from '../../../core/models/video.model';

@Component({
  selector: 'app-video-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  templateUrl: './video-detail.component.html',
  styleUrls: ['./video-detail.component.scss']
})
export class VideoDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly videoService = inject(VideoService);
  private readonly snackBar = inject(MatSnackBar);

  video: VideoResponse | null = null;
  isLoading = true;
  errorMessage: string | null = null;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadVideo(id);
    } else {
      this.errorMessage = 'Invalid video ID';
      this.isLoading = false;
    }
  }

  loadVideo(id: string): void {
    this.isLoading = true;
    this.videoService.getVideo(id).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.video = res.data;
        } else {
          this.errorMessage = res.message || 'Video not found';
        }
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error?.error?.message || 'Failed to load video details';
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
}
