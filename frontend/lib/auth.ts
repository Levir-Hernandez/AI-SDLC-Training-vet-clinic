import "server-only";
import { cache } from "react";
import { redirect } from "next/navigation";
import { ApiError, apiGet } from "./api";
import { getToken } from "./session";
import type { CurrentUser, Role } from "./types";

/** The signed-in user, or null when there is no valid session. */
export const getCurrentUser = cache(async (): Promise<CurrentUser | null> => {
  if (!(await getToken())) return null;
  try {
    return await apiGet<CurrentUser>("/api/auth/me", { allowUnauthorized: true });
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
});

/** Server-side guard for role areas: no session -> /login, wrong role -> /. */
export async function requireRole(...roles: Role[]): Promise<CurrentUser> {
  const user = await getCurrentUser();
  if (!user) redirect("/login");
  if (!roles.includes(user.role)) redirect("/");
  return user;
}
