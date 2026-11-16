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
  speakerLabel?: string;
  words?: WordDto[];
}

export interface JobProgressEvent {
  jobId: string;
  stage: string;
  progressPercent: number;
  completedChunks: number;
  totalChunks: number;
  message: string;
  timestamp: string;
}
