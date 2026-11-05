"use server";

import { redirect } from "next/navigation";
import { ApiError, apiFetch } from "@/lib/api";
import { clearSession, setSession } from "@/lib/session";
import type { LoginResponse } from "@/lib/types";

export interface SignInState {
  error?: string;
  fieldErrors?: Record<string, string>;
}

export async function signIn(
  _prev: SignInState,
  formData: FormData,
): Promise<SignInState> {
  const username = String(formData.get("username") ?? "");
  const password = String(formData.get("password") ?? "");
  try {
    const result = await apiFetch<LoginResponse>(
      "/api/auth/login",
      { method: "POST", body: JSON.stringify({ username, password }) },
      { redirectOn401: false },
    );
    await setSession(result.token, result.expiresAt);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 400) {
        const fieldErrors: Record<string, string> = {};
        for (const e of error.fieldErrors) fieldErrors[e.field] = e.message;
        return { error: "Please fill in the highlighted fields.", fieldErrors };
      }
      if (error.status === 401) return { error: "Invalid username or password." };
    }
    return { error: "Sign-in is unavailable right now. Try again later." };
  }
  redirect("/");
}

export async function signOut() {
  await clearSession();
  redirect("/login");
}
