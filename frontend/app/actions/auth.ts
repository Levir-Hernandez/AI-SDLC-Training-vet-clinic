"use server";

import { redirect } from "next/navigation";
import { ApiError, apiPost } from "@/lib/api";
import { clearSession, setSession } from "@/lib/session";
import type { LoginResponse } from "@/lib/types";

export type SignInState = {
  error?: string;
  fieldErrors?: Record<string, string>;
};

export async function signIn(
  _previous: SignInState,
  formData: FormData,
): Promise<SignInState> {
  const username = String(formData.get("username") ?? "").trim();
  const password = String(formData.get("password") ?? "");

  try {
    const login = await apiPost<LoginResponse>(
      "/api/auth/login",
      { username, password },
      { allowUnauthorized: true },
    );
    await setSession(login.token, login.expiresAt);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 400) {
        return {
          fieldErrors: Object.fromEntries(
            error.fieldErrors.map((e) => [e.field, e.message]),
          ),
        };
      }
      if (error.status === 401) return { error: "Invalid username or password." };
      return { error: error.message };
    }
    throw error;
  }
  redirect("/");
}

export async function signOut(): Promise<void> {
  await clearSession();
  redirect("/login");
}
