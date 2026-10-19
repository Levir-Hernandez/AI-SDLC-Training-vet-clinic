"use server";

import { redirect } from "next/navigation";
import { ApiError, apiPost } from "@/lib/api";
import type { Appointment, CreateAppointmentRequest } from "@/lib/types";

export type BookingResult = { error?: string; slotTaken?: boolean };

/** Books the appointment; on success redirects to its detail page. */
export async function bookAppointment(request: CreateAppointmentRequest): Promise<BookingResult> {
  let created: Appointment;
  try {
    created = await apiPost<Appointment>("/api/appointments", request);
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    if (error.code === "SLOT_TAKEN" || error.code === "SLOT_UNAVAILABLE") {
      return { slotTaken: true, error: "That slot was just taken. Pick another one." };
    }
    if (error.status === 403) return { error: "You are not allowed to book appointments." };
    return { error: error.message };
  }
  redirect(`/appointments/${created.id}`);
}
