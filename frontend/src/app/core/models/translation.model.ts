export interface TranslatedSegmentDto {
  segmentIndex: number;
  startTime: number;
  endTime: number;
  speakerLabel?: string;
  sourceText: string;
  translatedText: string;
  targetLanguage: string;
}

export interface TranslationResponse {
  id: string;
  jobId: string;
  sourceLanguage: string;
  targetLanguage: string;
  translationEngine: string;
  status: string;
  storageKey?: string;
  segments: TranslatedSegmentDto[];
  fullText: string;
  createdAt: string;
}

export interface CreateTranslationRequest {
  targetLanguage: string;
  engine?: string;
}
