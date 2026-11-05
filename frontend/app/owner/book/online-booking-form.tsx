"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, useTransition } from "react";
import { bookAppointment, confirmAppointment } from "@/app/actions/appointments";
import { ErrorMessage } from "@/components/ErrorMessage";
import { SlotPicker } from "@/components/SlotPicker";
import type { Pet, Service, Slot, Veterinarian } from "@/lib/types";

const FIELD =
  "rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900";

interface Reservation {
  id: number;
  reservedUntil: string | null;
}

function Countdown({ reservedUntil }: { reservedUntil: string }) {
  const [seconds, setSeconds] = useState<number | null>(null);
  useEffect(() => {
    // reservedUntil is a zone-less server timestamp in the clinic's (server) clock.
    const target = new Date(reservedUntil).getTime();
    const tick = () => setSeconds(Math.max(0, Math.round((target - Date.now()) / 1000)));
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [reservedUntil]);
  if (seconds === null) return null;
  const mm = String(Math.floor(seconds / 60)).padStart(2, "0");
  const ss = String(seconds % 60).padStart(2, "0");
  return <span aria-live="off">{seconds === 0 ? "Time is up" : `${mm}:${ss} left`}</span>;
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
  const router = useRouter();
  const [petId, setPetId] = useState("");
  const [slot, setSlot] = useState<Slot | null>(null);
  const [serviceId, setServiceId] = useState<number | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [reservation, setReservation] = useState<Reservation | null>(null);
  const [error, setError] = useState<string | undefined>();
  const [busy, startTransition] = useTransition();

  function reset(message?: string) {
    setReservation(null);
    setSlot(null);
    setError(message);
    setRefreshKey((k) => k + 1);
  }

  function reserve() {
    if (!slot || serviceId === null || !petId) return;
    setError(undefined);
    startTransition(async () => {
      const result = await bookAppointment({
        petId: Number(petId),
        serviceId,
        veterinarianId: slot.veterinarianId,
        startAt: slot.startAt,
      });
      if (result.id !== undefined) {
        setReservation({ id: result.id, reservedUntil: result.reservedUntil ?? null });
        return;
      }
      if (result.slotLost) reset(result.error);
      else setError(result.error);
    });
  }

  function confirm() {
    if (!reservation) return;
    setError(undefined);
    startTransition(async () => {
      const result = await confirmAppointment(reservation.id);
      if (result.ok) {
        router.push(`/appointments/${reservation.id}`);
        return;
      }
      if (result.expired) reset(result.error);
      else setError(result.error);
    });
  }

  if (reservation) {
    return (
      <section className="flex flex-col gap-4" aria-label="Reservation">
        <h2 className="text-xl font-semibold">Confirm your reservation</h2>
        <p className="text-sm">
          Your slot is held
          {reservation.reservedUntil && (
            <>
              {" "}
              for <Countdown reservedUntil={reservation.reservedUntil} />
            </>
          )}
          . Confirm it to schedule the appointment.
        </p>
        <ErrorMessage message={error} />
        <div>
          <button
            type="button"
            onClick={confirm}
            disabled={busy}
            className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
          >
            {busy ? "Confirming..." : "Confirm"}
          </button>
        </div>
      </section>
    );
  }

  return (
    <div className="flex flex-col gap-8">
      <section className="flex flex-col gap-3">
        <h2 className="text-xl font-semibold">1. Pet</h2>
        <label className="flex flex-col gap-1 text-sm">
          Pet
          <select value={petId} onChange={(e) => setPetId(e.target.value)} className={FIELD}>
            <option value="">Select a pet</option>
            {pets.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
      </section>
      <section className="flex flex-col gap-3">
        <h2 className="text-xl font-semibold">2. Service and slot</h2>
        <SlotPicker
          services={services}
          veterinarians={veterinarians}
          refreshKey={refreshKey}
          onSelect={(s, sid) => {
            setSlot(s);
            setServiceId(sid);
          }}
        />
      </section>
      <ErrorMessage message={error} />
      <div>
        <button
          type="button"
          onClick={reserve}
          disabled={busy || !slot || !petId}
          className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
        >
          {busy ? "Reserving..." : "Reserve"}
        </button>
      </div>
    </div>
  );
}
