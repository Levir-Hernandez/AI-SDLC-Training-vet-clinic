"use client";

import { useEffect, useState, useTransition } from "react";
import { fetchSlots } from "@/app/actions/availability";
import { ErrorMessage } from "@/components/ErrorMessage";
import type { Service, Slot, Veterinarian } from "@/lib/types";

const timeFormat = new Intl.DateTimeFormat("en", { hour: "2-digit", minute: "2-digit", hour12: false });

const slotKey = (s: Slot) => `${s.veterinarianId}|${s.startAt}`;

const todayIso = () => {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

type Props = {
  services: Service[];
  veterinarians: Veterinarian[];
  /** Called with the chosen service and slot; slot is null while nothing is selected. */
  onSelect: (selection: { serviceId: number | null; slot: Slot | null }) => void;
  /** Increment to force a reload of the slots (for example after a booking conflict). */
  refreshKey?: number;
  /** Preselects and locks the service (used when rescheduling). */
  lockedServiceId?: number;
};

/** Service, optional veterinarian and date selectors with the list of free slots below. */
export function SlotPicker({ services, veterinarians, onSelect, refreshKey = 0, lockedServiceId }: Props) {
  const [serviceId, setServiceId] = useState(lockedServiceId !== undefined ? String(lockedServiceId) : "");
  const [vetId, setVetId] = useState("");
  const [date, setDate] = useState("");
  const [slots, setSlots] = useState<Slot[] | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, startTransition] = useTransition();

  const ready = serviceId !== "" && date !== "";

  useEffect(() => {
    if (!ready) return;
    startTransition(async () => {
      const result = await fetchSlots(Number(serviceId), vetId ? Number(vetId) : null, date);
      setSelected(null);
      if (result.error !== undefined) {
        setError(result.error);
        setSlots(null);
      } else {
        setError(null);
        setSlots(result.slots);
      }
    });
  }, [ready, serviceId, vetId, date, refreshKey]);

  /** Any change of the filters invalidates the chosen slot. */
  function change(set: (value: string) => void, value: string, nextServiceId = serviceId) {
    set(value);
    setSelected(null);
    onSelect({ serviceId: nextServiceId ? Number(nextServiceId) : null, slot: null });
  }

  function choose(slot: Slot) {
    setSelected(slotKey(slot));
    onSelect({ serviceId: Number(serviceId), slot });
  }

  const field = "rounded border border-zinc-300 bg-transparent px-3 py-2 dark:border-zinc-700";

  return (
    <div className="flex flex-col gap-4">
      <div className="grid gap-4 sm:grid-cols-3">
        <div className="flex flex-col gap-1">
          <label htmlFor="serviceId" className="text-sm font-medium">Service</label>
          <select id="serviceId" value={serviceId} onChange={(e) => change(setServiceId, e.target.value, e.target.value)} disabled={lockedServiceId !== undefined} className={field}>
            <option value="">Select a service</option>
            {services.map((s) => (
              <option key={s.id} value={s.id}>{s.name} ({s.durationMinutes} min)</option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="veterinarianId" className="text-sm font-medium">Veterinarian</label>
          <select id="veterinarianId" value={vetId} onChange={(e) => change(setVetId, e.target.value)} className={field}>
            <option value="">Any veterinarian</option>
            {veterinarians.map((v) => (
              <option key={v.id} value={v.id}>{v.name}</option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="date" className="text-sm font-medium">Date</label>
          <input id="date" type="date" min={todayIso()} value={date} onChange={(e) => change(setDate, e.target.value)} className={field} />
        </div>
      </div>

      <div aria-live="polite">
        <ErrorMessage message={ready ? error : null} />
        {ready && loading ? <p className="text-zinc-500">Loading slots...</p> : null}
        {!ready ? (
          <p className="text-sm text-zinc-500">Choose a service and a date to see free slots.</p>
        ) : null}
        {ready && !loading && slots !== null && slots.length === 0 ? (
          <p className="text-zinc-500">No free slots for that selection.</p>
        ) : null}
        {ready && !loading && slots !== null && slots.length > 0 ? (
          <ul className="flex flex-wrap gap-2" aria-label="Free slots">
            {slots.map((slot) => {
              const key = slotKey(slot);
              return (
                <li key={key}>
                  <button
                    type="button"
                    aria-pressed={selected === key}
                    onClick={() => choose(slot)}
                    className={`rounded border px-3 py-2 text-sm ${
                      selected === key
                        ? "border-zinc-900 bg-zinc-900 text-white dark:border-zinc-100 dark:bg-zinc-100 dark:text-zinc-900"
                        : "border-zinc-300 dark:border-zinc-700"
                    }`}
                  >
                    {timeFormat.format(new Date(slot.startAt))} - {slot.veterinarianName}
                  </button>
                </li>
              );
            })}
          </ul>
        ) : null}
      </div>
    </div>
  );
}
