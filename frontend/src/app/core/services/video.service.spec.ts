import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { VideoService } from './video.service';
import { VideoResponse, InitiateUploadRequest, InitiateUploadResponse } from '../models/video.model';
import { ApiResponse, PageResponse } from '../models/api-response.model';

describe('VideoService', () => {
  let service: VideoService;
  let httpMock: HttpTestingController;

  const mockVideo: VideoResponse = {
    id: 'video-uuid-1',
    originalFilename: 'test_sample.mp4',
    fileSizeBytes: 1048576,
    durationSeconds: 120,
    codec: 'h264',
    resolution: '1080p',
    contentType: 'video/mp4',
    status: 'READY',
    downloadUrl: 'https://minio.local/bucket/video.mp4',
    createdAt: new Date().toISOString()
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [VideoService]
    });

    service = TestBed.inject(VideoService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should retrieve a single video by ID', () => {
    const apiResponse: ApiResponse<VideoResponse> = {
      success: true,
      data: mockVideo,
      timestamp: new Date().toISOString()
    };

    service.getVideo('video-uuid-1').subscribe(res => {
      expect(res.data.id).toBe('video-uuid-1');
      expect(res.data.originalFilename).toBe('test_sample.mp4');
    });

    const req = httpMock.expectOne('/api/v1/videos/video-uuid-1');
    expect(req.request.method).toBe('GET');
    req.flush(apiResponse);
  });

  it('should fetch paginated videos with optional status filter', () => {
    const pageData: PageResponse<VideoResponse> = {
      content: [mockVideo],
      pageNumber: 0,
      pageSize: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
      hasNext: false,
      hasPrevious: false
    };

    const apiResponse: ApiResponse<PageResponse<VideoResponse>> = {
      success: true,
      data: pageData,
      timestamp: new Date().toISOString()
    };

    service.listVideos('READY', 0, 20).subscribe(res => {
      expect(res.data.content.length).toBe(1);
      expect(res.data.totalElements).toBe(1);
    });

    const req = httpMock.expectOne(r => r.url === '/api/v1/videos' && r.params.get('status') === 'READY');
    expect(req.request.method).toBe('GET');
    req.flush(apiResponse);
  });

  it('should initiate multipart upload', () => {
    const request: InitiateUploadRequest = {
      filename: 'large_lecture.mp4',
      fileSizeBytes: 524288000,
      totalParts: 5
    };

    const mockInitResponse: InitiateUploadResponse = {
      uploadSessionId: 'session-uuid-1',
      videoId: 'video-uuid-2',
      partSize: 104857600,
      totalParts: 5
    };

    const apiResponse: ApiResponse<InitiateUploadResponse> = {
      success: true,
      data: mockInitResponse,
      timestamp: new Date().toISOString()
    };

    service.initiateMultipartUpload(request).subscribe(res => {
      expect(res.data.uploadSessionId).toBe('session-uuid-1');
      expect(res.data.totalParts).toBe(5);
    });

    const req = httpMock.expectOne('/api/v1/videos/upload/initiate');
    expect(req.request.method).toBe('POST');
    req.flush(apiResponse);
  });

  it('should delete video by ID', () => {
    let completed = false;
    service.deleteVideo('video-uuid-1').subscribe({
      next: () => { completed = true; },
      complete: () => { completed = true; }
    });

    const req = httpMock.expectOne('/api/v1/videos/video-uuid-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    expect(completed).toBeTrue();
  });
});
