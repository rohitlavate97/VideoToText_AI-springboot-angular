export interface CreateTranscriptionRequest {
  videoId: string;
  language?: string;
  engine?: string;
  model?: string;
  computeType?: string;
  enableDiarization?: boolean;
  maxSpeakers?: number;
  targetLanguages?: string[];
  generateSummary?: boolean;
}

export interface TranscriptionJobResponse {
  id: string;
  videoId: string;
  status: string;
  engine: string;
  model: string;
  language: string;
  currentStage: string;
  progressPercent: number;
  totalChunks: number;
  completedChunks: number;
  retryCount?: number;
  maxRetries?: number;
  errorMessage?: string;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  processingDurationMs?: number;
}

export interface WordDto {
  word: string;
  startTime: number;
  endTime: number;
  confidence: number;
}

export interface TranscriptSegmentResponse {
  id: string;
  segmentIndex: number;
  startTime: number;
  endTime: number;
  text: string;
  confidence: number;
  language: string;
  speakerId?: string;
  speakerLabel?: string;
  speakerDisplayName?: string;
  words?: WordDto[];
}

export interface UpdateTranscriptSegmentRequest {
  text?: string;
  startTime?: number;
  endTime?: number;
  speakerId?: string;
}

export interface TranscriptResponse {
  jobId: string;
  videoId: string;
  videoTitle: string;
  status: string;
  language: string;
  durationSeconds: number;
  totalSegments: number;
  totalWords: number;
  fullText: string;
  segments: TranscriptSegmentResponse[];
  completedAt?: string;
}

export interface TranscriptSearchResponse {
  query: string;
  totalMatches: number;
  matches: TranscriptSegmentResponse[];
}

export interface TranscriptRevision {
  id: string;
  jobId: string;
  segmentId?: string;
  userId: string;
  revisionType: string;
  fieldChanged: string;
  oldValue?: string;
  newValue?: string;
  createdAt: string;
}

export interface JobProgressEvent {
  jobId: string;
  stage: string;
  progressPercent: number;
  completedChunks: number;
  totalChunks: number;
  retryAttempt?: number;
  maxRetries?: number;
  errorMessage?: string;
  message: string;
  timestamp: string;
}

export interface ProcessingEventResponse {
  id: string;
  jobId: string;
  eventType: string;
  stage: string;
  progressPercent: number;
  message: string;
  metadataJson?: string;
  createdAt: string;
}
