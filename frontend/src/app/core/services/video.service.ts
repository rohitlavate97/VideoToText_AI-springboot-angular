import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEvent, HttpParams, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import {
  CompleteUploadRequest,
  InitiateUploadRequest,
  InitiateUploadResponse,
  VideoResponse
} from '../models/video.model';

@Injectable({
  providedIn: 'root'
})
export class VideoService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = '/api/v1/videos';

  /**
   * Upload video with direct multipart request and live upload progress events
   */
  uploadVideoWithProgress(file: File): Observable<HttpEvent<ApiResponse<VideoResponse>>> {
    const formData = new FormData();
    formData.append('file', file, file.name);

    const req = new HttpRequest<FormData>('POST', `${this.API_BASE}/upload`, formData, {
      reportProgress: true,
      responseType: 'json'
    });

    return this.http.request<ApiResponse<VideoResponse>>(req);
  }

  /**
   * Retrieves paginated list of videos
   */
  listVideos(
    status?: string,
    page: number = 0,
    size: number = 20
  ): Observable<ApiResponse<PageResponse<VideoResponse>>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (status && status !== 'ALL') {
      params = params.set('status', status);
    }

    return this.http.get<ApiResponse<PageResponse<VideoResponse>>>(this.API_BASE, { params });
  }

  /**
   * Retrieves single video details by ID
   */
  getVideo(id: string): Observable<ApiResponse<VideoResponse>> {
    return this.http.get<ApiResponse<VideoResponse>>(`${this.API_BASE}/${id}`);
  }

  /**
   * Deletes a video by ID
   */
  deleteVideo(id: string): Observable<void> {
    return this.http.delete<void>(`${this.API_BASE}/${id}`);
  }

  /**
   * Initiates multipart chunked upload session for large files (>100MB)
   */
  initiateMultipartUpload(request: InitiateUploadRequest): Observable<ApiResponse<InitiateUploadResponse>> {
    return this.http.post<ApiResponse<InitiateUploadResponse>>(`${this.API_BASE}/upload/initiate`, request);
  }

  /**
   * Uploads an individual chunk part
   */
  uploadPart(sessionId: string, partNumber: number, chunk: Blob): Observable<ApiResponse<{ partNumber: number; eTag: string }>> {
    const formData = new FormData();
    formData.append('file', chunk, `part-${partNumber}`);

    return this.http.put<ApiResponse<{ partNumber: number; eTag: string }>>(
      `${this.API_BASE}/upload/${sessionId}/parts/${partNumber}`,
      formData
    );
  }

  /**
   * Completes multipart upload session
   */
  completeMultipartUpload(sessionId: string, request: CompleteUploadRequest): Observable<ApiResponse<VideoResponse>> {
    return this.http.post<ApiResponse<VideoResponse>>(
      `${this.API_BASE}/upload/${sessionId}/complete`,
      request
    );
  }

  /**
   * Formats duration in seconds into human-readable string (e.g. "01:24:30" or "05:12")
   */
  formatDuration(seconds: number): string {
    if (!seconds || seconds <= 0) return '00:00';
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);

    const pad = (n: number) => n.toString().padStart(2, '0');
    if (hrs > 0) {
      return `${pad(hrs)}:${pad(mins)}:${pad(secs)}`;
    }
    return `${pad(mins)}:${pad(secs)}`;
  }

  /**
   * Formats file size in bytes into human-readable string (e.g. "45.2 MB", "1.4 GB")
   */
  formatFileSize(bytes: number): string {
    if (!bytes || bytes <= 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }
}
