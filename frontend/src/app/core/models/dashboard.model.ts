export interface RecentActivity {
  videoId: string;
  jobId: string;
  filename: string;
  status: string;
  durationSeconds: number;
  language: string;
  createdAt: string;
}

export interface DashboardStats {
  totalVideos: number;
  totalAudioDurationSeconds: number;
  totalTranscribedWords: number;
  totalExports: number;
  statusCounts: { [key: string]: number };
  languageDistribution: { [key: string]: number };
  recentActivity: RecentActivity[];
}
