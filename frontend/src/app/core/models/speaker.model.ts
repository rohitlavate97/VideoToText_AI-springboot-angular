export interface SpeakerResponse {
  id: string;
  jobId: string;
  speakerLabel: string;
  displayName: string;
  totalSpeakingTimeMs: number;
  segmentCount: number;
}

export interface UpdateSpeakerRequest {
  displayName: string;
}
