import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../models/api-response.model';
import { SpeakerResponse, UpdateSpeakerRequest } from '../models/speaker.model';

@Injectable({
  providedIn: 'root'
})
export class SpeakerService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = '/api/v1/transcriptions';

  getSpeakers(jobId: string): Observable<ApiResponse<SpeakerResponse[]>> {
    return this.http.get<ApiResponse<SpeakerResponse[]>>(`${this.API_BASE}/${jobId}/speakers`);
  }

  updateSpeaker(
    jobId: string,
    speakerId: string,
    request: UpdateSpeakerRequest
  ): Observable<ApiResponse<SpeakerResponse>> {
    return this.http.put<ApiResponse<SpeakerResponse>>(
      `${this.API_BASE}/${jobId}/speakers/${speakerId}`,
      request
    );
  }
}
