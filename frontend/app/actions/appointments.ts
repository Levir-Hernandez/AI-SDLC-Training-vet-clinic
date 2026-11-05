"use server";

import { ApiError, apiGet, apiPatch, apiPost } from "@/lib/api";
import type {
  Appointment,
  CreateAppointmentRequest,
  Owner,
  PageResponse,
  Pet,
} from "@/lib/types";

export interface BookResult {
  id?: number;
  error?: string;
  slotLost?: boolean;
  reservedUntil?: string | null;
}

export interface ConfirmResult {
  ok: boolean;
  error?: string;
  expired?: boolean;
}

export async function confirmAppointment(id: number): Promise<ConfirmResult> {
  try {
    await apiPost<Appointment>(`/api/appointments/${id}/confirm`);
    return { ok: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 409 && error.code === "RESERVATION_EXPIRED") {
        return { ok: false, expired: true, error: "Reservation expired, pick a slot again" };
      }
      if (error.status === 409) return { ok: false, error: error.message };
      if (error.status === 404) return { ok: false, error: "Appointment not found." };
    }
    return { ok: false, error: "Could not confirm the appointment. Try again later." };
  }
}

export interface CancelResult {
  ok: boolean;
  error?: string;
  reasonError?: string;
}

export async function cancelAppointment(id: number, reason: string): Promise<CancelResult> {
  try {
    await apiPost<Appointment>(`/api/appointments/${id}/cancel`, { reason });
    return { ok: true };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 400) {
        return { ok: false, reasonError: error.message, error: error.message };
      }
      if (error.status === 404) return { ok: false, error: "Appointment not found." };
      if (error.status === 403 || error.status === 409) {
        return { ok: false, error: error.message };
      }
    }
    return { ok: false, error: "Could not cancel the appointment. Try again later." };
  }
}

export interface SimpleResult {
  ok: boolean;
  error?: string;
}

async function visitAction(id: number, path: "start" | "finish"): Promise<SimpleResult> {
  try {
    await apiPost<Appointment>(`/api/appointments/${id}/${path}`);
    return { ok: true };
  } catch (error) {
    if (error instanceof ApiError && [403, 404, 409].includes(error.status)) {
      return { ok: false, error: error.message };
    }
    return { ok: false, error: "Could not update the appointment. Try again later." };
  }
}

export async function startAppointment(id: number): Promise<SimpleResult> {
  return visitAction(id, "start");
}

export async function finishAppointment(id: number): Promise<SimpleResult> {
  return visitAction(id, "finish");
}

export interface RescheduleResult {
  id?: number;
  error?: string;
  slotLost?: boolean;
}

export async function rescheduleAppointment(
  id: number,
  veterinarianId: number,
  startAt: string,
): Promise<RescheduleResult> {
  try {
    const created = await apiPost<Appointment>(`/api/appointments/${id}/reschedule`, {
      veterinarianId,
      startAt,
    });
    return { id: created.id };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 409 && ["SLOT_TAKEN", "SLOT_UNAVAILABLE"].includes(error.code)) {
        return { error: "That slot was just taken. Pick another one.", slotLost: true };
      }
      if ([400, 403, 404, 409].includes(error.status)) return { error: error.message };
    }
    return { error: "Could not reschedule the appointment. Try again later." };
  }
}

export interface ObservationsState {
  saved?: boolean;
  error?: string;
}

export async function saveObservations(
  _prev: ObservationsState,
  formData: FormData,
): Promise<ObservationsState> {
  const id = Number(formData.get("id"));
  const observations = String(formData.get("observations") ?? "");
  try {
    await apiPatch<Appointment>(`/api/appointments/${id}/observations`, { observations });
    return { saved: true };
  } catch (error) {
    if (error instanceof ApiError && [400, 403, 404, 409].includes(error.status)) {
      return { error: error.message };
    }
    return { error: "Could not save the observations. Try again later." };
  }
}

export async function bookAppointment(
  request: CreateAppointmentRequest,
): Promise<BookResult> {
  try {
    const created = await apiPost<Appointment>("/api/appointments", request);
    return { id: created.id, reservedUntil: created.reservedUntil };
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 409 && ["SLOT_TAKEN", "SLOT_UNAVAILABLE"].includes(error.code)) {
        return { error: "That slot was just taken. Pick another one.", slotLost: true };
      }
      if (error.status === 400) return { error: error.message };
      if (error.status === 403) return { error: "You are not allowed to book appointments." };
    }
    return { error: "Could not book the appointment. Try again later." };
  }
}

export async function searchOwners(q: string): Promise<Owner[]> {
  try {
    const page = await apiGet<PageResponse<Owner>>(
      `/api/owners?page=0&size=10&q=${encodeURIComponent(q.trim())}`,
    );
    return page.content;
  } catch {
    return [];
  }
}

export async function fetchOwnerPets(ownerId: number): Promise<Pet[]> {
  try {
    const page = await apiGet<PageResponse<Pet>>(`/api/pets?ownerId=${ownerId}&size=100`);
    return page.content;
  } catch {
    return [];
  }
}
