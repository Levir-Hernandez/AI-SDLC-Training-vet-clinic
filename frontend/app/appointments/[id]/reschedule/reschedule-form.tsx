"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { rescheduleAppointment } from "@/app/actions/appointments";
import { ErrorMessage } from "@/components/ErrorMessage";
import { SlotPicker } from "@/components/SlotPicker";
import type { Service, Slot, Veterinarian } from "@/lib/types";

export function RescheduleForm({
  appointmentId,
  serviceId,
  veterinarianId,
  services,
  veterinarians,
}: {
  appointmentId: number;
  serviceId: number;
  veterinarianId: number;
  services: Service[];
  veterinarians: Veterinarian[];
}) {
  const router = useRouter();
  const [slot, setSlot] = useState<Slot | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [error, setError] = useState<string | undefined>();
  const [pending, startTransition] = useTransition();

  function submit() {
    if (!slot) return;
    setError(undefined);
    startTransition(async () => {
      const result = await rescheduleAppointment(appointmentId, slot.veterinarianId, slot.startAt);
      if (result.id !== undefined) {
        router.push(`/appointments/${result.id}`);
        return;
      }
      setError(result.error);
      if (result.slotLost) {
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
        initialVeterinarianId={veterinarianId}
        refreshKey={refreshKey}
        onSelect={(s) => setSlot(s)}
      />
      <ErrorMessage message={error} />
      <div>
        <button
          type="button"
          onClick={submit}
          disabled={pending || !slot}
          className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
        >
          {pending ? "Rescheduling..." : "Reschedule"}
        </button>
      </div>
    </div>
  );
}
