import { Component, ElementRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { HttpEventType } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { VideoService } from '../../../core/services/video.service';
import { UploadState, VideoResponse } from '../../../core/models/video.model';

@Component({
  selector: 'app-video-upload',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatChipsModule
  ],
  templateUrl: './video-upload.component.html',
  styleUrls: ['./video-upload.component.scss']
})
export class VideoUploadComponent {
  private readonly videoService = inject(VideoService);
  private readonly router = inject(Router);

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  isDragOver = false;
  selectedFile: File | null = null;
  validationError: string | null = null;

  uploadState: UploadState = {
    state: 'idle',
    progress: 0
  };

  readonly allowedExtensions = ['mp4', 'mkv', 'avi', 'mov', 'webm', 'flv', 'wmv', 'mpeg', '3gp'];
  readonly maxFileSize = 10 * 1024 * 1024 * 1024; // 10 GB

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;

    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.handleFileSelection(files[0]);
    }
  }

  onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.handleFileSelection(input.files[0]);
    }
  }

  triggerFileInput(): void {
    if (this.uploadState.state !== 'uploading' && this.uploadState.state !== 'processing') {
      this.fileInput.nativeElement.click();
    }
  }

  handleFileSelection(file: File): void {
    this.validationError = null;
    this.uploadState = { state: 'idle', progress: 0 };

    const ext = file.name.substring(file.name.lastIndexOf('.') + 1).toLowerCase();
    if (!this.allowedExtensions.includes(ext)) {
      this.validationError = `Unsupported file format ".${ext}". Supported formats: ${this.allowedExtensions.join(', ')}`;
      this.selectedFile = null;
      return;
    }

    if (file.size <= 0) {
      this.validationError = 'The selected file is empty.';
      this.selectedFile = null;
      return;
    }

    if (file.size > this.maxFileSize) {
      this.validationError = `File size exceeds the 10 GB maximum limit (${this.formatFileSize(file.size)}).`;
      this.selectedFile = null;
      return;
    }

    this.selectedFile = file;
  }

  startUpload(): void {
    if (!this.selectedFile) return;

    this.uploadState = {
      state: 'uploading',
      progress: 0,
      totalBytes: this.selectedFile.size,
      uploadedBytes: 0
    };

    this.videoService.uploadVideoWithProgress(this.selectedFile).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress) {
          if (event.total) {
            const progress = Math.round((100 * event.loaded) / event.total);
            this.uploadState = {
              ...this.uploadState,
              progress,
              uploadedBytes: event.loaded,
              totalBytes: event.total
            };

            if (progress >= 100) {
              this.uploadState.state = 'processing';
            }
          }
        } else if (event.type === HttpEventType.Response) {
          const body = event.body;
          if (body && body.success) {
            this.uploadState = {
              state: 'completed',
              progress: 100,
              video: body.data
            };
          } else {
            this.uploadState = {
              state: 'error',
              progress: 0,
              errorMessage: body?.message || 'Upload failed'
            };
          }
        }
      },
      error: (err) => {
        const errorMsg =
          err?.error?.error?.message ||
          err?.error?.message ||
          err?.message ||
          'Upload failed. Please check your network and try again.';
        this.uploadState = {
          state: 'error',
          progress: 0,
          errorMessage: errorMsg
        };
      }
    });
  }

  resetUpload(): void {
    this.selectedFile = null;
    this.validationError = null;
    this.uploadState = { state: 'idle', progress: 0 };
    if (this.fileInput) {
      this.fileInput.nativeElement.value = '';
    }
  }

  formatFileSize(bytes: number): string {
    return this.videoService.formatFileSize(bytes);
  }

  formatDuration(seconds: number): string {
    return this.videoService.formatDuration(seconds);
  }
}
