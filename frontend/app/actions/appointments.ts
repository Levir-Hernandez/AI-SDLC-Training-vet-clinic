"use server";

import { ApiError, apiGet, apiPost } from "@/lib/api";
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
}

export async function bookAppointment(
  request: CreateAppointmentRequest,
): Promise<BookResult> {
  try {
    const created = await apiPost<Appointment>("/api/appointments", request);
    return { id: created.id };
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
