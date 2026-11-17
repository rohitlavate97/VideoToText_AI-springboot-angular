import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../models/api-response.model';
import { ExportResponse, GenerateSubtitleRequest, SubtitleResponse } from '../models/subtitle.model';

@Injectable({
  providedIn: 'root'
})
export class SubtitleService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/transcriptions';

  getSubtitles(jobId: string): Observable<ApiResponse<SubtitleResponse[]>> {
    return this.http.get<ApiResponse<SubtitleResponse[]>>(`${this.baseUrl}/${jobId}/subtitles`);
  }

  generateSubtitle(jobId: string, request: GenerateSubtitleRequest): Observable<ApiResponse<SubtitleResponse>> {
    return this.http.post<ApiResponse<SubtitleResponse>>(`${this.baseUrl}/${jobId}/subtitles`, request);
  }

  downloadSubtitle(jobId: string, format: string, language: string, includeSpeakers: boolean): Observable<Blob> {
    const params = new HttpParams()
      .set('format', format)
      .set('language', language)
      .set('includeSpeakers', includeSpeakers.toString());
    return this.http.get(`${this.baseUrl}/${jobId}/subtitles/download`, {
      params,
      responseType: 'blob'
    });
  }

  downloadSubtitleById(jobId: string, subtitleId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${jobId}/subtitles/${subtitleId}/download`, {
      responseType: 'blob'
    });
  }

  deleteSubtitle(jobId: string, subtitleId: string): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/${jobId}/subtitles/${subtitleId}`);
  }

  exportTranscript(jobId: string, format: string, language: string): Observable<Blob> {
    const params = new HttpParams()
      .set('format', format)
      .set('language', language);
    return this.http.get(`${this.baseUrl}/${jobId}/export`, {
      params,
      responseType: 'blob'
    });
  }

  getExports(jobId: string): Observable<ApiResponse<ExportResponse[]>> {
    return this.http.get<ApiResponse<ExportResponse[]>>(`${this.baseUrl}/${jobId}/exports`);
  }

  saveBlob(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }
}
