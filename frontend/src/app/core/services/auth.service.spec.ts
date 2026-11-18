import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { TokenService } from './token.service';
import { AuthResponse, LoginRequest, RegisterRequest } from '../models/user.model';
import { ApiResponse } from '../models/api-response.model';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let tokenServiceSpy: jasmine.SpyObj<TokenService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const mockAuthResponse: AuthResponse = {
    accessToken: 'mock-access-token-123',
    refreshToken: 'mock-refresh-token-456',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: {
      id: 'user-uuid-1',
      email: 'tester@example.com',
      firstName: 'Test',
      lastName: 'User',
      roles: ['ROLE_USER']
    }
  };

  beforeEach(() => {
    tokenServiceSpy = jasmine.createSpyObj('TokenService', [
      'setTokens',
      'clearTokens',
      'hasAccessToken',
      'getRefreshToken'
    ]);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AuthService,
        { provide: TokenService, useValue: tokenServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should authenticate user and store tokens on login', () => {
    const loginReq: LoginRequest = { email: 'tester@example.com', password: 'Password123!' };
    const apiResponse: ApiResponse<AuthResponse> = {
      success: true,
      data: mockAuthResponse,
      timestamp: new Date().toISOString()
    };

    service.login(loginReq).subscribe(res => {
      expect(res.accessToken).toBe('mock-access-token-123');
      expect(tokenServiceSpy.setTokens).toHaveBeenCalledWith('mock-access-token-123', 'mock-refresh-token-456');
    });

    const req = httpMock.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(loginReq);
    req.flush(apiResponse);
  });

  it('should register user and save tokens on successful registration', () => {
    const regReq: RegisterRequest = {
      email: 'newuser@example.com',
      password: 'Password123!',
      firstName: 'New',
      lastName: 'User'
    };
    const apiResponse: ApiResponse<AuthResponse> = {
      success: true,
      data: mockAuthResponse,
      timestamp: new Date().toISOString()
    };

    service.register(regReq).subscribe(res => {
      expect(res.user.email).toBe('tester@example.com');
      expect(tokenServiceSpy.setTokens).toHaveBeenCalled();
    });

    const req = httpMock.expectOne('/api/v1/auth/register');
    expect(req.request.method).toBe('POST');
    req.flush(apiResponse);
  });

  it('should clear tokens, reset user state, and navigate to login on logout', () => {
    service.logout();

    expect(tokenServiceSpy.clearTokens).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });
});
