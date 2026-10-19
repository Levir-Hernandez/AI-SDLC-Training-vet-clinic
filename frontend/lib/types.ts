// API types mirroring the backend DTOs. Keep this the single place for them.

export type Role = "OWNER" | "RECEPTIONIST" | "VETERINARIAN";

export type CurrentUser = {
  userId: number;
  username: string;
  role: Role;
  displayName: string;
};

export type LoginResponse = {
  token: string;
  expiresAt: string;
  role: Role;
  displayName: string;
};

export type FieldError = { field: string; message: string };

/** RFC 9457 problem detail returned by the backend, with its `code` and per-field `errors`. */
export type ProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  errors?: FieldError[];
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
};

export type Weekday =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

export type Service = {
  id: number;
  code: string;
  name: string;
  durationMinutes: number;
  basePrice: number;
};

/** `start` and `end` arrive as "HH:mm" or "HH:mm:ss". */
export type ScheduleBlock = {
  day: Weekday;
  start: string;
  end: string;
};

export type Veterinarian = {
  id: number;
  name: string;
  specialization: string;
  costPerService: number;
  schedule: ScheduleBlock[];
};

export type CreateVeterinarianRequest = {
  name: string;
  specialization: string;
  costPerService: number | null;
  username: string;
  password: string;
  schedule: ScheduleBlock[];
};
