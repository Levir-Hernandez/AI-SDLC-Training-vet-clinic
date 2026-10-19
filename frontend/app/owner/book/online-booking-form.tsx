"use client";

import { useEffect, useState, useTransition } from "react";
import { bookAppointment, confirmAppointment } from "@/app/actions/appointments";
import { ErrorMessage } from "@/components/ErrorMessage";
import { SlotPicker } from "@/components/SlotPicker";
import type { Appointment, Pet, Service, Slot, Veterinarian } from "@/lib/types";

function useCountdown(until: string | null): number | null {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!until) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [until]);
  if (!until) return null;
  return Math.max(0, Math.floor((new Date(until).getTime() - now) / 1000));
}

export function OnlineBookingForm({
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
  const [reservation, setReservation] = useState<Appointment | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [pending, startTransition] = useTransition();
  const remaining = useCountdown(reservation?.reservedUntil ?? null);

  function reset(message: string | null) {
    setReservation(null);
    setSlot(null);
    setError(message);
    setRefreshKey((k) => k + 1);
  }

  function reserve() {
    if (!petId || serviceId === null || !slot) return;
    setError(null);
    startTransition(async () => {
      const result = await bookAppointment(
        { petId: Number(petId), serviceId, veterinarianId: slot.veterinarianId, startAt: slot.startAt },
        { redirectOnSuccess: false },
      );
      if (result.appointment) {
        setReservation(result.appointment);
        return;
      }
      if (result.slotTaken) reset(result.error ?? null);
      else setError(result.error ?? "Could not reserve the slot.");
    });
  }

  function confirm() {
    if (!reservation) return;
    setError(null);
    startTransition(async () => {
      // Success redirects to the detail page; reaching here means a failure.
      const result = await confirmAppointment(reservation.id);
      if (result?.expired) reset(result.error ?? null);
      else if (result?.error) setError(result.error);
    });
  }

  const expired = remaining === 0;
  const field = "rounded border border-zinc-300 bg-transparent px-3 py-2 dark:border-zinc-700";
  const left = remaining ?? 0;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex max-w-sm flex-col gap-1">
        <label htmlFor="petId" className="text-sm font-medium">Pet</label>
        <select
          id="petId"
          value={petId}
          onChange={(e) => setPetId(e.target.value)}
          disabled={reservation !== null}
          className={field}
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
          setReservation(null);
        }}
      />

      <ErrorMessage message={error} />

      {reservation ? (
        <section aria-live="polite" className="flex flex-col gap-3 rounded border border-zinc-300 p-4 dark:border-zinc-700">
          <h2 className="font-semibold">Slot reserved</h2>
          <p className="text-sm">
            {expired
              ? "Reservation expired, pick a slot again"
              : `Confirm within ${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")} to keep this slot.`}
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={confirm}
              disabled={pending || expired}
              className="rounded bg-zinc-900 px-4 py-2 text-white disabled:opacity-60 dark:bg-zinc-100 dark:text-zinc-900"
            >
              {pending ? "Confirming..." : "Confirm"}
            </button>
            {expired ? (
              <button type="button" onClick={() => reset(null)} className="rounded border border-zinc-300 px-4 py-2 dark:border-zinc-700">
                Pick another slot
              </button>
            ) : null}
          </div>
        </section>
      ) : (
        <button
          type="button"
          onClick={reserve}
          disabled={pending || !petId || !slot}
          className="self-start rounded bg-zinc-900 px-4 py-2 text-white disabled:opacity-60 dark:bg-zinc-100 dark:text-zinc-900"
        >
          {pending ? "Reserving..." : "Reserve"}
        </button>
      )}
    </div>
  );
}
