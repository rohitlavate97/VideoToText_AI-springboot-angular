import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../models/api-response.model';
import { GenerateSummaryRequest, SummaryResponse } from '../models/summary.model';

@Injectable({
  providedIn: 'root'
})
export class SummaryService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = '/api/v1/transcriptions';

  getSummary(jobId: string): Observable<ApiResponse<SummaryResponse>> {
    return this.http.get<ApiResponse<SummaryResponse>>(`${this.API_BASE}/${jobId}/summary`);
  }

  generateSummary(jobId: string, request?: GenerateSummaryRequest): Observable<ApiResponse<SummaryResponse>> {
    return this.http.post<ApiResponse<SummaryResponse>>(
      `${this.API_BASE}/${jobId}/summary`,
      request || {}
    );
  }

  deleteSummary(jobId: string): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.API_BASE}/${jobId}/summary`);
  }
}
