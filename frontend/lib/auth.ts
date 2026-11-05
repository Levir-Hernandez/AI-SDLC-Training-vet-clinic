import "server-only";
import { redirect } from "next/navigation";
import { ApiError, apiFetch } from "./api";
import { getToken } from "./session";
import type { CurrentUser, Role } from "./types";

/** Returns the signed-in user, or null when there is no valid session. */
export async function getCurrentUser(): Promise<CurrentUser | null> {
  if (!(await getToken())) return null;
  try {
    return await apiFetch<CurrentUser>("/api/auth/me", {}, {
      redirectOn401: false,
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
}

export async function requireRole(...roles: Role[]): Promise<CurrentUser> {
  const user = await getCurrentUser();
  if (!user) redirect("/login");
  if (!roles.includes(user.role)) redirect("/");
  return user;
}
