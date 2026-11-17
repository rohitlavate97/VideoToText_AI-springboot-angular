export interface ActionItemDto {
  task: string;
  assignee?: string;
  timestamp?: number;
  completed?: boolean;
}

export interface TopicDto {
  name: string;
  relevance: number;
  startTime?: number;
}

export interface ChapterDto {
  title: string;
  startTime: number;
  endTime: number;
  summary: string;
}

export interface SummaryResponse {
  id: string;
  jobId: string;
  summaryText: string;
  summaryLength: string;
  summaryFormat: string;
  keyPoints: string[];
  actionItems: ActionItemDto[];
  topics: TopicDto[];
  chapters: ChapterDto[];
  llmProvider: string;
  llmModel?: string;
  createdAt: string;
  updatedAt: string;
}

export interface GenerateSummaryRequest {
  summaryLength?: string;
  summaryFormat?: string;
  extractKeyPoints?: boolean;
  extractActionItems?: boolean;
  extractTopics?: boolean;
  generateChapters?: boolean;
  provider?: string;
}
