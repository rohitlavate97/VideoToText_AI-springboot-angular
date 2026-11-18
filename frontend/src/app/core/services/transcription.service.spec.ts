import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TranscriptionService } from './transcription.service';
import {
  CreateTranscriptionRequest,
  TranscriptionJobResponse,
  TranscriptResponse,
  TranscriptSegmentResponse,
  UpdateTranscriptSegmentRequest
} from '../models/transcription.model';
import { ApiResponse } from '../models/api-response.model';

describe('TranscriptionService', () => {
  let service: TranscriptionService;
  let httpMock: HttpTestingController;

  const mockJob: TranscriptionJobResponse = {
    id: 'job-uuid-1',
    videoId: 'video-uuid-1',
    status: 'QUEUED',
    currentStage: 'INITIALIZED',
    progressPercent: 0,
    totalChunks: 1,
    completedChunks: 0,
    language: 'en',
    engine: 'faster-whisper',
    model: 'small',
    createdAt: new Date().toISOString()
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [TranscriptionService]
    });

    service = TestBed.inject(TranscriptionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should submit transcription job request', () => {
    const request: CreateTranscriptionRequest = {
      videoId: 'video-uuid-1',
      engine: 'faster-whisper',
      model: 'small',
      language: 'en',
      enableDiarization: true
    };

    const apiResponse: ApiResponse<TranscriptionJobResponse> = {
      success: true,
      data: mockJob,
      timestamp: new Date().toISOString()
    };

    service.createJob(request).subscribe(res => {
      expect(res.data.id).toBe('job-uuid-1');
      expect(res.data.status).toBe('QUEUED');
    });

    const req = httpMock.expectOne('/api/v1/transcriptions');
    expect(req.request.method).toBe('POST');
    req.flush(apiResponse);
  });

  it('should fetch transcription job by ID', () => {
    const apiResponse: ApiResponse<TranscriptionJobResponse> = {
      success: true,
      data: { ...mockJob, status: 'PROCESSING', progressPercent: 45.5 },
      timestamp: new Date().toISOString()
    };

    service.getJob('job-uuid-1').subscribe(res => {
      expect(res.data.status).toBe('PROCESSING');
      expect(res.data.progressPercent).toBe(45.5);
    });

    const req = httpMock.expectOne('/api/v1/transcriptions/job-uuid-1');
    expect(req.request.method).toBe('GET');
    req.flush(apiResponse);
  });

  it('should fetch full transcript data for a job', () => {
    const mockTranscript: TranscriptResponse = {
      jobId: 'job-uuid-1',
      videoId: 'video-uuid-1',
      videoTitle: 'Test Video',
      status: 'COMPLETED',
      language: 'en',
      durationSeconds: 120,
      totalSegments: 2,
      totalWords: 15,
      fullText: 'Hello and welcome to this video transcript test.',
      segments: []
    };

    const apiResponse: ApiResponse<TranscriptResponse> = {
      success: true,
      data: mockTranscript,
      timestamp: new Date().toISOString()
    };

    service.getTranscript('job-uuid-1').subscribe(res => {
      expect(res.data.totalWords).toBe(15);
      expect(res.data.fullText).toContain('video transcript');
    });

    const req = httpMock.expectOne('/api/v1/transcriptions/job-uuid-1/transcript');
    expect(req.request.method).toBe('GET');
    req.flush(apiResponse);
  });

  it('should update segment text', () => {
    const updateReq: UpdateTranscriptSegmentRequest = { text: 'Corrected subtitle text' };
    const mockSegment: TranscriptSegmentResponse = {
      id: 'seg-uuid-1',
      segmentIndex: 0,
      startTime: 0,
      endTime: 5,
      text: 'Corrected subtitle text',
      language: 'en',
      confidence: 0.99
    };

    const apiResponse: ApiResponse<TranscriptSegmentResponse> = {
      success: true,
      data: mockSegment,
      timestamp: new Date().toISOString()
    };

    service.updateSegment('job-uuid-1', 'seg-uuid-1', updateReq).subscribe(res => {
      expect(res.data.text).toBe('Corrected subtitle text');
    });

    const req = httpMock.expectOne('/api/v1/transcriptions/job-uuid-1/segments/seg-uuid-1');
    expect(req.request.method).toBe('PUT');
    req.flush(apiResponse);
  });

  it('should cancel active transcription job', () => {
    const cancelledJob = { ...mockJob, status: 'CANCELLED' };
    const apiResponse: ApiResponse<TranscriptionJobResponse> = {
      success: true,
      data: cancelledJob,
      timestamp: new Date().toISOString()
    };

    service.cancelJob('job-uuid-1').subscribe(res => {
      expect(res.data.status).toBe('CANCELLED');
    });

    const req = httpMock.expectOne('/api/v1/transcriptions/job-uuid-1/cancel');
    expect(req.request.method).toBe('POST');
    req.flush(apiResponse);
  });
});
