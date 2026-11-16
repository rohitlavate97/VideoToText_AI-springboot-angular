import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { ApiResponse } from '../models/api-response.model';
import {
  UpdatePreferenceRequest,
  UpdateProfileRequest,
  UserPreferenceResponse,
  UserProfile
} from '../models/user.model';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly API_BASE = '/api/v1/users';

  getProfile(): Observable<UserProfile> {
    return this.http.get<ApiResponse<UserProfile>>(`${this.API_BASE}/me`).pipe(
      map(res => res.data)
    );
  }

  updateProfile(request: UpdateProfileRequest): Observable<UserProfile> {
    return this.http.put<ApiResponse<UserProfile>>(`${this.API_BASE}/me`, request).pipe(
      map(res => res.data)
    );
  }

  updatePreferences(request: UpdatePreferenceRequest): Observable<UserPreferenceResponse> {
    return this.http.put<ApiResponse<UserPreferenceResponse>>(`${this.API_BASE}/me/preferences`, request).pipe(
      map(res => res.data)
    );
  }
}
