"use client";

import { useEffect, useState, useTransition } from "react";
import { fetchSlots } from "@/app/actions/availability";
import type { Service, Slot, Veterinarian } from "@/lib/types";
import { ErrorMessage } from "./ErrorMessage";

function today(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function timeLabel(iso: string): string {
  return iso.slice(11, 16);
}

const FIELD =
  "rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900";

export function SlotPicker({
  services,
  veterinarians,
  refreshKey = 0,
  onSelect,
  lockedServiceId,
  initialVeterinarianId,
}: {
  services: Service[];
  veterinarians: Veterinarian[];
  /** Fixed service: initial value, select disabled. */
  lockedServiceId?: number;
  initialVeterinarianId?: number;
  /** Change to force a reload of the slots (e.g. after a lost race). */
  refreshKey?: number;
  onSelect: (slot: Slot | null, serviceId: number | null) => void;
}) {
  const [serviceId, setServiceId] = useState(
    lockedServiceId !== undefined ? String(lockedServiceId) : "",
  );
  const [vetId, setVetId] = useState(
    initialVeterinarianId !== undefined ? String(initialVeterinarianId) : "",
  );
  const [date, setDate] = useState(today);
  const [slots, setSlots] = useState<Slot[] | null>(null);
  const [error, setError] = useState<string | undefined>();
  const [selected, setSelected] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();

  useEffect(() => {
    if (!serviceId || !date) return;
    startTransition(async () => {
      const result = await fetchSlots(Number(serviceId), vetId ? Number(vetId) : null, date);
      setSlots(result.slots);
      setError(result.error);
      setSelected(null);
    });
  }, [serviceId, vetId, date, refreshKey]);

  /** Any change of criteria drops the current selection. */
  function change(setter: (value: string) => void, value: string, nextServiceId: string) {
    setter(value);
    setSelected(null);
    onSelect(null, nextServiceId ? Number(nextServiceId) : null);
  }

  function choose(slot: Slot) {
    setSelected(`${slot.veterinarianId}|${slot.startAt}`);
    onSelect(slot, Number(serviceId));
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-4">
        <label className="flex flex-col gap-1 text-sm">
          Service
          <select
            value={serviceId}
            disabled={lockedServiceId !== undefined}
            onChange={(e) => change(setServiceId, e.target.value, e.target.value)}
            className={FIELD}
          >
            <option value="">Select a service</option>
            {services.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} ({s.durationMinutes} min)
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Veterinarian
          <select value={vetId} onChange={(e) => change(setVetId, e.target.value, serviceId)} className={FIELD}>
            <option value="">Any veterinarian</option>
            {veterinarians.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Date
          <input
            type="date"
            value={date}
            min={today()}
            onChange={(e) => change(setDate, e.target.value, serviceId)}
            className={FIELD}
          />
        </label>
      </div>
      <div aria-live="polite">
        {!serviceId ? (
          <p className="text-sm text-zinc-600 dark:text-zinc-400">Select a service to see free slots.</p>
        ) : pending ? (
          <p className="text-sm">Loading slots...</p>
        ) : error ? (
          <ErrorMessage message={error} />
        ) : slots && slots.length === 0 ? (
          <p className="text-sm text-zinc-600 dark:text-zinc-400">No free slots for this date.</p>
        ) : (
          <ul className="flex flex-wrap gap-2" aria-label="Free slots">
            {(slots ?? []).map((s) => {
              const key = `${s.veterinarianId}|${s.startAt}`;
              return (
                <li key={key}>
                  <button
                    type="button"
                    aria-pressed={selected === key}
                    onClick={() => choose(s)}
                    className={`rounded border px-3 py-1 text-sm ${
                      selected === key
                        ? "border-blue-600 bg-blue-600 text-white"
                        : "border-zinc-300 dark:border-zinc-700"
                    }`}
                  >
                    {timeLabel(s.startAt)}-{timeLabel(s.endAt)} {s.veterinarianName}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
