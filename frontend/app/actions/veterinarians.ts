"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { ApiError, apiPost } from "@/lib/api";
import type { CreateVeterinarianRequest, ScheduleBlock, Veterinarian, Weekday } from "@/lib/types";

export type CreateVeterinarianState = {
  error?: string;
  /** Message for the schedule section as a whole (overlaps, end before start). */
  scheduleError?: string;
  fieldErrors?: Record<string, string>;
};

/** Reads the repeated day/start/end inputs of the schedule rows. */
function readSchedule(formData: FormData): ScheduleBlock[] {
  const days = formData.getAll("day");
  const starts = formData.getAll("start");
  const ends = formData.getAll("end");
  return days.map((day, i) => ({
    day: String(day) as Weekday,
    start: String(starts[i] ?? ""),
    end: String(ends[i] ?? ""),
  }));
}

export async function createVeterinarian(
  _previous: CreateVeterinarianState,
  formData: FormData,
): Promise<CreateVeterinarianState> {
  const rawCost = String(formData.get("costPerService") ?? "").trim();
  const request: CreateVeterinarianRequest = {
    name: String(formData.get("name") ?? "").trim(),
    specialization: String(formData.get("specialization") ?? "").trim(),
    costPerService: rawCost === "" ? null : Number(rawCost),
    username: String(formData.get("username") ?? "").trim(),
    password: String(formData.get("password") ?? ""),
    schedule: readSchedule(formData),
  };

  try {
    await apiPost<Veterinarian>("/api/veterinarians", request);
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    if (error.code === "INVALID_SCHEDULE") return { scheduleError: error.message };
    if (error.code === "USERNAME_TAKEN") {
      return { fieldErrors: { username: "This username is already taken." } };
    }
    if (error.status === 400 && error.fieldErrors.length > 0) {
      const fieldErrors: Record<string, string> = {};
      let scheduleError: string | undefined;
      for (const e of error.fieldErrors) {
        if (e.field.startsWith("schedule")) scheduleError = scheduleError ?? e.message;
        else fieldErrors[e.field] = e.message;
      }
      return { fieldErrors, scheduleError };
    }
    if (error.status === 403) return { error: "Only reception can register veterinarians." };
    return { error: error.message };
  }

  revalidatePath("/reception/veterinarians");
  redirect("/reception/veterinarians");
}
