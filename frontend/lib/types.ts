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
}

export type Weekday =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

export interface Service {
  id: number;
  code: string;
  name: string;
  durationMinutes: number;
  basePrice: number;
}

export interface ScheduleBlock {
  day: Weekday;
  start: string;
  end: string;
}

export interface Veterinarian {
  id: number;
  name: string;
  specialization: string;
  costPerService: number;
  schedule: ScheduleBlock[];
}

export interface CreateVeterinarianRequest {
  name: string;
  specialization: string;
  costPerService: number;
  username: string;
  password: string;
  schedule: ScheduleBlock[];
}
