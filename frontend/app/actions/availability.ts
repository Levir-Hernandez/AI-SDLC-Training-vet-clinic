"use server";

import { ApiError, apiGet } from "@/lib/api";
import type { Slot } from "@/lib/types";

export type SlotsResult = { slots: Slot[]; error?: undefined } | { slots?: undefined; error: string };

/** Free slots for a service, an optional veterinarian and a date (yyyy-MM-dd). */
export async function fetchSlots(
  serviceId: number,
  veterinarianId: number | null,
  date: string,
): Promise<SlotsResult> {
  const params = new URLSearchParams({ serviceId: String(serviceId), date });
  if (veterinarianId !== null) params.set("veterinarianId", String(veterinarianId));
  try {
    return { slots: await apiGet<Slot[]>(`/api/availability?${params}`) };
  } catch (error) {
    if (error instanceof ApiError) return { error: error.message };
    throw error;
  }
}
