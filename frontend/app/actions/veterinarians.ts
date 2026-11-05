"use server";

import { revalidatePath } from "next/cache";
import { ApiError, apiPost } from "@/lib/api";
import type { ScheduleBlock, Veterinarian, Weekday } from "@/lib/types";

export interface CreateVeterinarianState {
  error?: string;
  success?: boolean;
  fieldErrors?: Record<string, string>;
}

export async function createVeterinarian(
  _prev: CreateVeterinarianState,
  formData: FormData,
): Promise<CreateVeterinarianState> {
  const days = formData.getAll("day").map(String);
  const starts = formData.getAll("start").map(String);
  const ends = formData.getAll("end").map(String);
  const schedule: ScheduleBlock[] = days.map((day, i) => ({
    day: day as Weekday,
    start: starts[i] ?? "",
    end: ends[i] ?? "",
  }));
  const cost = String(formData.get("costPerService") ?? "").trim();
  const body = {
    name: String(formData.get("name") ?? ""),
    specialization: String(formData.get("specialization") ?? ""),
    costPerService: cost === "" ? null : Number(cost),
    username: String(formData.get("username") ?? ""),
    password: String(formData.get("password") ?? ""),
    schedule,
  };
  try {
    await apiPost<Veterinarian>("/api/veterinarians", body);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 400 && error.fieldErrors.length > 0) {
        const fieldErrors: Record<string, string> = {};
        for (const e of error.fieldErrors) fieldErrors[e.field] = e.message;
        return { error: "Please fix the highlighted fields.", fieldErrors };
      }
      if (error.status === 400 || error.status === 409) {
        return { error: error.message };
      }
      if (error.status === 403) {
        return { error: "You are not allowed to register veterinarians." };
      }
    }
    return { error: "Could not register the veterinarian. Try again later." };
  }
  revalidatePath("/reception/veterinarians");
  return { success: true };
}
