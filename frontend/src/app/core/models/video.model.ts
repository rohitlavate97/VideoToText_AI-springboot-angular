export interface VideoResponse {
  id: string;
  originalFilename: string;
  fileSizeBytes: number;
  durationSeconds: number;
  codec: string;
  resolution: string;
  contentType: string;
  status: VideoStatus;
  downloadUrl?: string;
  createdAt: string;
}

export type VideoStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED' | 'ARCHIVED';

export interface InitiateUploadRequest {
  filename: string;
  fileSizeBytes: number;
  contentType?: string;
  totalParts: number;
}

export interface InitiateUploadResponse {
  uploadSessionId: string;
  videoId: string;
  partSize: number;
  totalParts: number;
}

export interface CompleteUploadRequest {
  parts: {
    partNumber: number;
    eTag: string;
  }[];
}

export interface UploadState {
  state: 'idle' | 'uploading' | 'processing' | 'completed' | 'error';
  progress: number;
  uploadedBytes?: number;
  totalBytes?: number;
  errorMessage?: string;
  video?: VideoResponse;
}
