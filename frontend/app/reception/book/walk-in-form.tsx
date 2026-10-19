"use client";

import { useState, useTransition } from "react";
import { bookAppointment } from "@/app/actions/appointments";
import { ErrorMessage } from "@/components/ErrorMessage";
import { SlotPicker } from "@/components/SlotPicker";
import type { Pet, Service, Slot, Veterinarian } from "@/lib/types";

export function WalkInForm({
  pets,
  services,
  veterinarians,
}: {
  pets: Pet[];
  services: Service[];
  veterinarians: Veterinarian[];
}) {
  const [petId, setPetId] = useState(pets.length === 1 ? String(pets[0].id) : "");
  const [serviceId, setServiceId] = useState<number | null>(null);
  const [slot, setSlot] = useState<Slot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [pending, startTransition] = useTransition();

  function submit() {
    if (!petId || serviceId === null || !slot) return;
    setError(null);
    startTransition(async () => {
      const result = await bookAppointment({
        petId: Number(petId),
        serviceId,
        veterinarianId: slot.veterinarianId,
        startAt: slot.startAt,
      });
      // A successful booking redirects; reaching here means a failure.
      if (result?.error) setError(result.error);
      if (result?.slotTaken) {
        setSlot(null);
        setRefreshKey((k) => k + 1);
      }
    });
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex max-w-sm flex-col gap-1">
        <label htmlFor="petId" className="text-sm font-medium">Pet</label>
        <select
          id="petId"
          value={petId}
          onChange={(e) => setPetId(e.target.value)}
          className="rounded border border-zinc-300 bg-transparent px-3 py-2 dark:border-zinc-700"
        >
          <option value="">Select a pet</option>
          {pets.map((p) => (
            <option key={p.id} value={p.id}>{p.name}</option>
          ))}
        </select>
      </div>

      <SlotPicker
        services={services}
        veterinarians={veterinarians}
        refreshKey={refreshKey}
        onSelect={(selection) => {
          setServiceId(selection.serviceId);
          setSlot(selection.slot);
        }}
      />

      {/* The refresh clears the selection, so the message set after a conflict is kept separately. */}
      <ErrorMessage message={error} />
      <button
        type="button"
        onClick={submit}
        disabled={pending || !petId || !slot}
        className="self-start rounded bg-zinc-900 px-4 py-2 text-white disabled:opacity-60 dark:bg-zinc-100 dark:text-zinc-900"
      >
        {pending ? "Booking..." : "Book walk-in"}
      </button>
    </div>
  );
}
