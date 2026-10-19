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
