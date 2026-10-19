"use server";

import { redirect } from "next/navigation";
import { ApiError, apiPost } from "@/lib/api";
import type { Appointment, CreateAppointmentRequest } from "@/lib/types";

export type BookingResult = { error?: string; slotTaken?: boolean; appointment?: Appointment };
export type ConfirmResult = { error?: string; expired?: boolean };

/** Books the appointment; on success redirects to its detail page. */
export async function bookAppointment(
  request: CreateAppointmentRequest,
  options: { redirectOnSuccess?: boolean } = {},
): Promise<BookingResult> {
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
  if (options.redirectOnSuccess === false) return { appointment: created };
  redirect(`/appointments/${created.id}`);
}

/** Confirms a reservation; on success redirects to the appointment detail. */
export async function confirmAppointment(id: number): Promise<ConfirmResult> {
  try {
    await apiPost<Appointment>(`/api/appointments/${id}/confirm`);
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    if (error.code === "RESERVATION_EXPIRED") {
      return { expired: true, error: "Reservation expired, pick a slot again" };
    }
    return { error: error.message };
  }
  redirect(`/appointments/${id}`);
}

/** Form-action variant used by the Confirm button on the detail page; reloads the page either way. */
export async function confirmFromDetail(id: number, formData: FormData): Promise<void> {
  void formData;
  await confirmAppointment(id);
  redirect(`/appointments/${id}`);
}
