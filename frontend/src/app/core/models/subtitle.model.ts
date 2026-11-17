export type SubtitleFormat = 'SRT' | 'VTT' | 'TXT' | 'JSON';

export interface SubtitleResponse {
  id: string;
  jobId: string;
  format: string;
  language: string;
  storageKey: string;
  includesSpeakers: boolean;
  content?: string;
  createdAt: string;
  updatedAt: string;
}

export interface GenerateSubtitleRequest {
  format: string;
  language?: string;
  includeSpeakers?: boolean;
}

export interface ExportResponse {
  id: string;
  jobId: string;
  userId: string;
  exportType: string;
  format: string;
  language: string;
  storageKey: string;
  fileSizeBytes: number;
  createdAt: string;
}
