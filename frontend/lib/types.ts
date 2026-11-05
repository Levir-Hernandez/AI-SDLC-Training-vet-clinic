export type Role = "OWNER" | "RECEPTIONIST" | "VETERINARIAN";

export interface CurrentUser {
  userId: number;
  username: string;
  role: Role;
  displayName: string;
  ownerId: number | null;
  veterinarianId: number | null;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  role: Role;
  displayName: string;
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ProblemDetail {
  status: number;
  title?: string;
  detail?: string;
  code?: string;
  errors?: FieldError[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
