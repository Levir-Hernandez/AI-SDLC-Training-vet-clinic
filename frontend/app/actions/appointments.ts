"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { ApiError, apiPatch, apiPost } from "@/lib/api";
import type { Appointment, CreateAppointmentRequest } from "@/lib/types";

export type BookingResult = { error?: string; slotTaken?: boolean; appointment?: Appointment };
export type CancelResult = { error?: string; reasonError?: string };
export type ConfirmResult = { error?: string; expired?: boolean };
export type PayResult = { error?: string; amountError?: string };
export type ObservationsState = { error?: string; saved?: boolean };

async function runVisitAction(id: number, action: "start" | "finish"): Promise<void> {
  try {
    await apiPost<Appointment>(`/api/appointments/${id}/${action}`);
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    // The page reloads with the current state and allowed actions, so a stale click is harmless.
  }
  revalidatePath(`/appointments/${id}`);
  redirect(`/appointments/${id}`);
}

/** Form-action variant used by the Start button on the detail page. */
export async function startAppointment(id: number, formData: FormData): Promise<void> {
  void formData;
  await runVisitAction(id, "start");
}

/** Form-action variant used by the Finish button on the detail page. */
export async function finishAppointment(id: number, formData: FormData): Promise<void> {
  void formData;
  await runVisitAction(id, "finish");
}

/** Saves the observations text; used with `useActionState`. */
export async function saveObservations(
  id: number,
  _previous: ObservationsState,
  formData: FormData,
): Promise<ObservationsState> {
  const observations = String(formData.get("observations") ?? "");
  try {
    await apiPatch<Appointment>(`/api/appointments/${id}/observations`, { observations });
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    const field = error.fieldErrors.find((e) => e.field === "observations");
    if (field) return { error: field.message };
    if (error.code === "OBSERVATIONS_LOCKED") return { error: "Observations can no longer be edited." };
    return { error: error.message };
  }
  revalidatePath(`/appointments/${id}`);
  return { saved: true };
}

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

/** Cancels the appointment with a reason; on success redirects to its detail page. */
export async function cancelAppointment(id: number, reason: string): Promise<CancelResult> {
  try {
    await apiPost<Appointment>(`/api/appointments/${id}/cancel`, { reason });
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    const field = error.fieldErrors.find((e) => e.field === "reason");
    if (field) return { reasonError: field.message };
    return { error: error.message };
  }
  redirect(`/appointments/${id}`);
}

/** Records the exact payment; the dialog shows `AMOUNT_MISMATCH` / `ALREADY_PAID` messages. */
export async function payAppointment(id: number, amount: string): Promise<PayResult> {
  try {
    await apiPost(`/api/appointments/${id}/payments`, { amount });
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    const field = error.fieldErrors.find((e) => e.field === "amount");
    if (field) return { amountError: field.message };
    if (error.code === "AMOUNT_MISMATCH") return { error: "The amount must equal the appointment total exactly." };
    if (error.code === "ALREADY_PAID") return { error: "This appointment was already paid." };
    if (error.code === "INVALID_TRANSITION") return { error: "This appointment can no longer be paid." };
    return { error: error.message };
  }
  revalidatePath(`/appointments/${id}`);
  revalidatePath("/reception/appointments");
  redirect(`/appointments/${id}`);
}

/** Form-action variant of close: Paid -> Closed, or Finished -> Closed as a debt. */
export async function closeAppointment(id: number, formData: FormData): Promise<void> {
  void formData;
  try {
    await apiPost<Appointment>(`/api/appointments/${id}/close`);
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    // The page reloads with the current state and allowed actions, so a stale click is harmless.
  }
  revalidatePath(`/appointments/${id}`);
  revalidatePath("/reception/appointments");
  revalidatePath("/reception/debts");
  redirect(`/appointments/${id}`);
}

/** Reschedules or reassigns; on success redirects to the new linked appointment. */
export async function rescheduleAppointment(
  id: number,
  veterinarianId: number,
  startAt: string,
): Promise<BookingResult> {
  let created: Appointment;
  try {
    created = await apiPost<Appointment>(`/api/appointments/${id}/reschedule`, { veterinarianId, startAt });
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    if (error.code === "SLOT_TAKEN" || error.code === "SLOT_UNAVAILABLE") {
      return { slotTaken: true, error: "That slot was just taken. Pick another one." };
    }
    if (error.code === "INVALID_TRANSITION") return { error: "This appointment can no longer be rescheduled." };
    return { error: error.message };
  }
  revalidatePath(`/appointments/${id}`);
  redirect(`/appointments/${created.id}`);
}
