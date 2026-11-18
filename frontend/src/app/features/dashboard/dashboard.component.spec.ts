import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardComponent } from './dashboard.component';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { DashboardStats } from '../../core/models/dashboard.model';
import { ApiResponse } from '../../core/models/api-response.model';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let dashboardServiceSpy: jasmine.SpyObj<DashboardService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const mockStats: DashboardStats = {
    totalVideos: 12,
    totalAudioDurationSeconds: 7200,
    totalTranscribedWords: 24500,
    totalExports: 8,
    statusCounts: {
      COMPLETED: 10,
      PROCESSING: 1,
      QUEUED: 1,
      FAILED: 0
    },
    languageDistribution: {
      en: 8,
      hi: 3,
      mr: 1
    },
    recentActivity: [
      {
        videoId: 'vid-1',
        jobId: 'job-1',
        filename: 'Interview 1.mp4',
        status: 'COMPLETED',
        durationSeconds: 60,
        language: 'en',
        createdAt: new Date().toISOString()
      }
    ]
  };

  beforeEach(async () => {
    dashboardServiceSpy = jasmine.createSpyObj('DashboardService', ['getStats']);
    authServiceSpy = jasmine.createSpyObj('AuthService', ['logout'], {
      currentUser$: of({
        id: 'user-1',
        email: 'user@example.com',
        firstName: 'John',
        lastName: 'Doe',
        roles: ['ROLE_USER']
      })
    });

    const apiResponse: ApiResponse<DashboardStats> = {
      success: true,
      data: mockStats,
      timestamp: new Date().toISOString()
    };
    dashboardServiceSpy.getStats.and.returnValue(of(apiResponse));

    await TestBed.configureTestingModule({
      imports: [DashboardComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        { provide: DashboardService, useValue: dashboardServiceSpy },
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the dashboard component', () => {
    expect(component).toBeTruthy();
  });

  it('should load dashboard statistics on init', () => {
    expect(dashboardServiceSpy.getStats).toHaveBeenCalled();
    expect(component.stats).toEqual(mockStats);
    expect(component.isLoadingStats).toBeFalse();
    expect(component.stats?.totalVideos).toBe(12);
  });

  it('should format seconds into human-readable duration strings', () => {
    expect(component.formatDuration(0)).toBe('0m');
    expect(component.formatDuration(59)).toBe('0m');
    expect(component.formatDuration(120)).toBe('2m');
    expect(component.formatDuration(3665)).toBe('1h 1m');
    expect(component.formatDuration(7200)).toBe('2h 0m');
  });

  it('should handle dashboard statistics fetch error gracefully', () => {
    dashboardServiceSpy.getStats.and.returnValue(throwError(() => new Error('Server error')));
    component.loadStats();

    expect(component.isLoadingStats).toBeFalse();
  });
});
