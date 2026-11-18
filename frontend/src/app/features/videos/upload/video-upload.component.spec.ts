import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VideoUploadComponent } from './video-upload.component';
import { VideoService } from '../../../core/services/video.service';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

describe('VideoUploadComponent', () => {
  let component: VideoUploadComponent;
  let fixture: ComponentFixture<VideoUploadComponent>;
  let videoServiceSpy: jasmine.SpyObj<VideoService>;

  beforeEach(async () => {
    videoServiceSpy = jasmine.createSpyObj('VideoService', [
      'uploadVideoWithProgress',
      'formatFileSize',
      'formatDuration'
    ]);
    videoServiceSpy.formatFileSize.and.callFake((bytes: number) => {
      if (!bytes || bytes <= 0) return '0 B';
      const k = 1024;
      const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
      const i = Math.floor(Math.log(bytes) / Math.log(k));
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    });

    await TestBed.configureTestingModule({
      imports: [VideoUploadComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        { provide: VideoService, useValue: videoServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(VideoUploadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the video upload component', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize in idle upload state', () => {
    expect(component.uploadState.state).toBe('idle');
    expect(component.uploadState.progress).toBe(0);
    expect(component.selectedFile).toBeNull();
    expect(component.validationError).toBeNull();
  });

  it('should accept valid mp4 file under maximum size', () => {
    const validFile = new File(['dummy-video-content'], 'sample_video.mp4', { type: 'video/mp4' });

    component.handleFileSelection(validFile);

    expect(component.validationError).toBeNull();
    expect(component.selectedFile).toBe(validFile);
  });

  it('should reject file with unsupported extension', () => {
    const invalidFile = new File(['text-content'], 'notes.txt', { type: 'text/plain' });

    component.handleFileSelection(invalidFile);

    expect(component.selectedFile).toBeNull();
    expect(component.validationError).toContain('Unsupported file format');
  });

  it('should clear selected file and reset upload state', () => {
    const validFile = new File(['dummy-video-content'], 'sample_video.mp4', { type: 'video/mp4' });
    component.handleFileSelection(validFile);
    expect(component.selectedFile).not.toBeNull();

    component.resetUpload();

    expect(component.selectedFile).toBeNull();
    expect(component.uploadState.state).toBe('idle');
    expect(component.validationError).toBeNull();
  });

  it('should format file size bytes correctly', () => {
    expect(component.formatFileSize(0)).toBe('0 B');
    expect(component.formatFileSize(1024)).toBe('1 KB');
    expect(component.formatFileSize(1048576)).toBe('1 MB');
    expect(component.formatFileSize(1073741824)).toBe('1 GB');
  });
});
