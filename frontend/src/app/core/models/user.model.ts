export interface UserSummary {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
}

export interface UserPreferences {
  defaultLanguage: string;
  processingMode: 'local' | 'cloud';
  preferredAsrEngine: string;
  preferredAsrModel: string;
}

export type UserPreferenceResponse = UserPreferences;

export interface UserProfile {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
  preferences: UserPreferences;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserSummary;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface UpdateProfileRequest {
  firstName?: string;
  lastName?: string;
}

export interface UpdatePreferenceRequest {
  defaultLanguage?: string;
  processingMode?: 'local' | 'cloud';
  preferredAsrEngine?: string;
  preferredAsrModel?: string;
}
