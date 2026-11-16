import { Injectable, inject, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../models/api-response.model';
import {
  CreateTranscriptionRequest,
  JobProgressEvent,
  TranscriptSegmentResponse,
  TranscriptionJobResponse
} from '../models/transcription.model';

@Injectable({
  providedIn: 'root'
})
export class TranscriptionService {
  private readonly http = inject(HttpClient);
  private readonly ngZone = inject(NgZone);
  private readonly API_BASE = '/api/v1/transcriptions';
  private readonly EVENTS_BASE = '/api/v1/events';

  createJob(request: CreateTranscriptionRequest): Observable<ApiResponse<TranscriptionJobResponse>> {
    return this.http.post<ApiResponse<TranscriptionJobResponse>>(this.API_BASE, request);
  }

  getJob(jobId: string): Observable<ApiResponse<TranscriptionJobResponse>> {
    return this.http.get<ApiResponse<TranscriptionJobResponse>>(`${this.API_BASE}/${jobId}`);
  }

  getUserJobs(): Observable<ApiResponse<TranscriptionJobResponse[]>> {
    return this.http.get<ApiResponse<TranscriptionJobResponse[]>>(this.API_BASE);
  }

  getVideoJobs(videoId: string): Observable<ApiResponse<TranscriptionJobResponse[]>> {
    return this.http.get<ApiResponse<TranscriptionJobResponse[]>>(`${this.API_BASE}/video/${videoId}`);
  }

  getJobSegments(jobId: string): Observable<ApiResponse<TranscriptSegmentResponse[]>> {
    return this.http.get<ApiResponse<TranscriptSegmentResponse[]>>(`${this.API_BASE}/${jobId}/segments`);
  }

  cancelJob(jobId: string): Observable<ApiResponse<TranscriptionJobResponse>> {
    return this.http.post<ApiResponse<TranscriptionJobResponse>>(`${this.API_BASE}/${jobId}/cancel`, {});
  }

  /**
   * Subscribes to real-time Server-Sent Events (SSE) for job progress updates.
   */
  subscribeToJobProgress(jobId: string): Observable<JobProgressEvent> {
    return new Observable<JobProgressEvent>((observer) => {
      const eventSource = new EventSource(`${this.EVENTS_BASE}/jobs/${jobId}`);

      eventSource.addEventListener('PROGRESS', (event: MessageEvent) => {
        this.ngZone.run(() => {
          try {
            const data: JobProgressEvent = JSON.parse(event.data);
            observer.next(data);
          } catch (e) {
            console.error('Error parsing SSE progress data:', e);
          }
        });
      });

      eventSource.addEventListener('COMPLETED', (event: MessageEvent) => {
        this.ngZone.run(() => {
          try {
            const data = JSON.parse(event.data);
            observer.next({
              jobId: data.jobId,
              stage: 'COMPLETED',
              progressPercent: 100,
              completedChunks: 0,
              totalChunks: 0,
              message: data.message || 'Transcription finalized successfully.',
              timestamp: new Date().toISOString()
            });
            observer.complete();
          } catch (e) {
            observer.complete();
          }
          eventSource.close();
        });
      });

      eventSource.onerror = (err) => {
        this.ngZone.run(() => {
          // If already complete or network glitch, close
          if (eventSource.readyState === EventSource.CLOSED) {
            observer.complete();
          } else {
            console.warn('SSE connection warning for job:', jobId, err);
          }
        });
      };

      return () => {
        eventSource.close();
      };
    });
  }
}
