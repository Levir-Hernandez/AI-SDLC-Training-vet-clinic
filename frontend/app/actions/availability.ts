"use server";

import { ApiError, apiGet } from "@/lib/api";
import type { Slot } from "@/lib/types";

export interface SlotsResult {
  slots: Slot[];
  error?: string;
}

export async function fetchSlots(
  serviceId: number,
  veterinarianId: number | null,
  date: string,
): Promise<SlotsResult> {
  const params = new URLSearchParams({ serviceId: String(serviceId), date });
  if (veterinarianId !== null) params.set("veterinarianId", String(veterinarianId));
  try {
    return { slots: await apiGet<Slot[]>(`/api/availability?${params.toString()}`) };
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return { slots: [], error: "Service or veterinarian not found." };
    }
    return { slots: [], error: "Could not load available slots. Try again later." };
  }
}
