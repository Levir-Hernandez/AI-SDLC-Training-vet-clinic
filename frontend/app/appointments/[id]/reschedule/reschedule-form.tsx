"use client";

import { useState, useTransition } from "react";
import { rescheduleAppointment } from "@/app/actions/appointments";
import { ErrorMessage } from "@/components/ErrorMessage";
import { SlotPicker } from "@/components/SlotPicker";
import type { Service, Slot, Veterinarian } from "@/lib/types";

export function RescheduleForm({
  appointmentId,
  serviceId,
  services,
  veterinarians,
}: {
  appointmentId: number;
  serviceId: number;
  services: Service[];
  veterinarians: Veterinarian[];
}) {
  const [slot, setSlot] = useState<Slot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [pending, startTransition] = useTransition();

  function submit() {
    if (!slot) return;
    setError(null);
    startTransition(async () => {
      const result = await rescheduleAppointment(appointmentId, slot.veterinarianId, slot.startAt);
      // A successful reschedule redirects; reaching here means a failure.
      if (result?.error) setError(result.error);
      if (result?.slotTaken) {
        setSlot(null);
        setRefreshKey((k) => k + 1);
      }
    });
  }

  return (
    <div className="flex flex-col gap-6">
      <SlotPicker
        services={services}
        veterinarians={veterinarians}
        lockedServiceId={serviceId}
        refreshKey={refreshKey}
        onSelect={(selection) => setSlot(selection.slot)}
      />
      <ErrorMessage message={error} />
      <button
        type="button"
        onClick={submit}
        disabled={pending || !slot}
        className="self-start rounded bg-zinc-900 px-4 py-2 text-white disabled:opacity-60 dark:bg-zinc-100 dark:text-zinc-900"
      >
        {pending ? "Rescheduling..." : "Reschedule"}
      </button>
    </div>
  );
}
