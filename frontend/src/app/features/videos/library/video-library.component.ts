import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { VideoService } from '../../../core/services/video.service';
import { VideoResponse } from '../../../core/models/video.model';

@Component({
  selector: 'app-video-library',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule
  ],
  templateUrl: './video-library.component.html',
  styleUrls: ['./video-library.component.scss']
})
export class VideoLibraryComponent implements OnInit {
  private readonly videoService = inject(VideoService);
  private readonly snackBar = inject(MatSnackBar);

  videos: VideoResponse[] = [];
  filteredVideos: VideoResponse[] = [];
  isLoading = true;
  searchTerm = '';
  selectedStatus = 'ALL';

  // Pagination
  currentPage = 0;
  pageSize = 12;
  totalPages = 0;
  totalElements = 0;

  readonly statusFilters = ['ALL', 'UPLOADED', 'PROCESSING', 'READY', 'FAILED'];

  ngOnInit(): void {
    this.loadVideos();
  }

  loadVideos(): void {
    this.isLoading = true;
    this.videoService.listVideos(this.selectedStatus, this.currentPage, this.pageSize).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.videos = res.data.content || [];
          this.totalPages = res.data.totalPages || 0;
          this.totalElements = res.data.totalElements || 0;
          this.applySearchFilter();
        }
        this.isLoading = false;
      },
      error: (err) => {
        this.isLoading = false;
        this.snackBar.open('Failed to load video library', 'Close', { duration: 4000 });
      }
    });
  }

  onFilterStatus(status: string): void {
    this.selectedStatus = status;
    this.currentPage = 0;
    this.loadVideos();
  }

  onSearchChange(): void {
    this.applySearchFilter();
  }

  applySearchFilter(): void {
    if (!this.searchTerm.trim()) {
      this.filteredVideos = [...this.videos];
    } else {
      const term = this.searchTerm.toLowerCase().trim();
      this.filteredVideos = this.videos.filter((v) =>
        v.originalFilename.toLowerCase().includes(term) ||
        v.codec?.toLowerCase().includes(term) ||
        v.status.toLowerCase().includes(term)
      );
    }
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages) {
      this.currentPage = page;
      this.loadVideos();
    }
  }

  deleteVideo(video: VideoResponse, event: Event): void {
    event.stopPropagation();
    if (confirm(`Are you sure you want to delete "${video.originalFilename}"? This will permanently remove the video file and its metadata.`)) {
      this.videoService.deleteVideo(video.id).subscribe({
        next: () => {
          this.snackBar.open(`"${video.originalFilename}" deleted successfully`, 'OK', { duration: 3000 });
          this.loadVideos();
        },
        error: (err) => {
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
