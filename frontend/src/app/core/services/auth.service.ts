import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, catchError, map, of, tap, throwError } from 'rxjs';
import { TokenService } from './token.service';
import {
  AuthResponse,
  LoginRequest,
  RefreshTokenRequest,
  RegisterRequest,
  UserSummary
} from '../models/user.model';
import { ApiResponse } from '../models/api-response.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokenService = inject(TokenService);
  private readonly router = inject(Router);

  private readonly API_BASE = '/api/v1/auth';
  private readonly USER_STORAGE_KEY = 'videototext_current_user';

  private readonly currentUserSubject = new BehaviorSubject<UserSummary | null>(this.getStoredUser());
  readonly currentUser$ = this.currentUserSubject.asObservable();

  private readonly isAuthenticatedSubject = new BehaviorSubject<boolean>(this.tokenService.hasAccessToken());
  readonly isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.API_BASE}/register`, request).pipe(
      map(res => res.data),
      tap(authData => this.handleAuthSuccess(authData))
    );
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.API_BASE}/login`, request).pipe(
      map(res => res.data),
      tap(authData => this.handleAuthSuccess(authData))
    );
  }

  refreshToken(): Observable<AuthResponse> {
    const refreshToken = this.tokenService.getRefreshToken();
    if (!refreshToken) {
      this.logout();
      return throwError(() => new Error('No refresh token available'));
    }

    const payload: RefreshTokenRequest = { refreshToken };
    return this.http.post<ApiResponse<AuthResponse>>(`${this.API_BASE}/refresh`, payload).pipe(
      map(res => res.data),
      tap(authData => this.handleAuthSuccess(authData)),
      catchError(err => {
        this.logout();
        return throwError(() => err);
      })
    );
  }

  logout(): void {
    const refreshToken = this.tokenService.getRefreshToken();
    if (refreshToken) {
      this.http.post(`${this.API_BASE}/logout`, { refreshToken }).subscribe({
        error: () => {} // Ignore failure on logout
      });
    }

    this.tokenService.clearTokens();
    localStorage.removeItem(this.USER_STORAGE_KEY);
    this.currentUserSubject.next(null);
    this.isAuthenticatedSubject.next(false);
    this.router.navigate(['/login']);
  }

  get currentUser(): UserSummary | null {
    return this.currentUserSubject.value;
  }

  get isAuthenticated(): boolean {
    return this.tokenService.hasAccessToken();
  }

  private handleAuthSuccess(authData: AuthResponse): void {
    this.tokenService.setTokens(authData.accessToken, authData.refreshToken);
    localStorage.setItem(this.USER_STORAGE_KEY, JSON.stringify(authData.user));
    this.currentUserSubject.next(authData.user);
    this.isAuthenticatedSubject.next(true);
  }

  private getStoredUser(): UserSummary | null {
    const json = localStorage.getItem(this.USER_STORAGE_KEY);
    if (!json) return null;
    try {
      return JSON.parse(json);
    } catch {
      return null;
    }
  }
}
