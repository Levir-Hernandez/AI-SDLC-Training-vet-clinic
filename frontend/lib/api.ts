import "server-only";
import { redirect } from "next/navigation";
import { clearSession, getToken } from "./session";
import type { FieldError, ProblemDetail } from "./types";

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string | undefined,
    message: string,
    public readonly fieldErrors: FieldError[] = [],
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type ApiInit = Omit<RequestInit, "body"> & {
  body?: unknown;
  /** Do not redirect to /login on a 401 (used by sign-in and the current-user probe). */
  allowUnauthorized?: boolean;
};

/** The only place that talks to the backend; runs on the server and forwards the session token. */
export async function apiFetch<T>(path: string, init: ApiInit = {}): Promise<T> {
  const { body, allowUnauthorized, headers, ...rest } = init;
  const token = await getToken();
  const requestHeaders = new Headers(headers);
  requestHeaders.set("Accept", "application/json");
  if (body !== undefined) requestHeaders.set("Content-Type", "application/json");
  if (token) requestHeaders.set("Authorization", `Bearer ${token}`);

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...rest,
      headers: requestHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
    });
  } catch {
    throw new ApiError(0, "BACKEND_UNREACHABLE", "The server cannot be reached. Try again later.");
  }

  if (response.status === 401 && !allowUnauthorized) {
    try {
      await clearSession(); // only permitted in server actions / route handlers
    } catch {
      // rendering a page: the proxy and /login handle the stale cookie
    }
    redirect("/login");
  }

  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as ProblemDetail;
    throw new ApiError(
      response.status,
      problem.code,
      problem.detail ?? problem.title ?? `Request failed with status ${response.status}`,
      problem.errors ?? [],
    );
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const apiGet = <T>(path: string, init?: ApiInit) =>
  apiFetch<T>(path, { ...init, method: "GET" });

export const apiPost = <T>(path: string, body?: unknown, init?: ApiInit) =>
  apiFetch<T>(path, { ...init, method: "POST", body });

export const apiPatch = <T>(path: string, body?: unknown, init?: ApiInit) =>
  apiFetch<T>(path, { ...init, method: "PATCH", body });
