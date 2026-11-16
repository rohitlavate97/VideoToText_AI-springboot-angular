import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse } from '../models/api-response.model';
import { CreateTranslationRequest, TranslationResponse } from '../models/translation.model';

@Injectable({
  providedIn: 'root'
})
export class TranslationService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = '/api/v1/transcriptions';

  getTranslations(jobId: string): Observable<ApiResponse<TranslationResponse[]>> {
    return this.http.get<ApiResponse<TranslationResponse[]>>(`${this.API_BASE}/${jobId}/translations`);
  }

  getTranslation(jobId: string, language: string): Observable<ApiResponse<TranslationResponse>> {
    return this.http.get<ApiResponse<TranslationResponse>>(`${this.API_BASE}/${jobId}/translations/${language}`);
  }

  requestTranslation(jobId: string, request: CreateTranslationRequest): Observable<ApiResponse<TranslationResponse>> {
    return this.http.post<ApiResponse<TranslationResponse>>(`${this.API_BASE}/${jobId}/translations`, request);
  }
}
