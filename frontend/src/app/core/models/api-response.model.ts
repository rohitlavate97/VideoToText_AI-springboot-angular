export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface ValidationErrorDetail {
  field: string;
  rejectedValue?: any;
  reason: string;
}

export interface ErrorDetails {
  code: string;
  message: string;
  details?: ValidationErrorDetail[];
}

export interface ErrorResponse {
  success: boolean;
  error: ErrorDetails;
  timestamp: string;
}
