"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import {
  bookAppointment,
  fetchOwnerPets,
  searchOwners,
} from "@/app/actions/appointments";
import { ErrorMessage } from "@/components/ErrorMessage";
import { SlotPicker } from "@/components/SlotPicker";
import type { Owner, Pet, Service, Slot, Veterinarian } from "@/lib/types";

const FIELD =
  "rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900";

export function WalkInForm({
  services,
  veterinarians,
}: {
  services: Service[];
  veterinarians: Veterinarian[];
}) {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [owners, setOwners] = useState<Owner[] | null>(null);
  const [ownerId, setOwnerId] = useState("");
  const [pets, setPets] = useState<Pet[]>([]);
  const [petId, setPetId] = useState("");
  const [slot, setSlot] = useState<Slot | null>(null);
  const [serviceId, setServiceId] = useState<number | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [error, setError] = useState<string | undefined>();
  const [searching, startSearch] = useTransition();
  const [booking, startBooking] = useTransition();

  function search() {
    startSearch(async () => {
      setOwners(await searchOwners(query));
    });
  }

  function pickOwner(id: string) {
    setOwnerId(id);
    setPetId("");
    setPets([]);
    if (!id) return;
    startSearch(async () => {
      setPets(await fetchOwnerPets(Number(id)));
    });
  }

  function book() {
    if (!slot || serviceId === null || !petId) return;
    setError(undefined);
    startBooking(async () => {
      const result = await bookAppointment({
        petId: Number(petId),
        serviceId,
        veterinarianId: slot.veterinarianId,
        startAt: slot.startAt,
      });
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
    <div className="flex flex-col gap-8">
      <section className="flex flex-col gap-3">
        <h2 className="text-xl font-semibold">1. Owner and pet</h2>
        <div className="flex gap-2">
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                search();
              }
            }}
            aria-label="Search owner by name or phone"
            placeholder="Search owner by name or phone"
            className={`flex-1 ${FIELD}`}
          />
          <button
            type="button"
            onClick={search}
            className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700"
          >
            Search
          </button>
        </div>
        {searching && <p className="text-sm">Loading...</p>}
        {owners !== null && owners.length === 0 && <p className="text-sm">No owners found.</p>}
        {owners !== null && owners.length > 0 && (
          <label className="flex flex-col gap-1 text-sm">
            Owner
            <select value={ownerId} onChange={(e) => pickOwner(e.target.value)} className={FIELD}>
              <option value="">Select an owner</option>
              {owners.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.name} ({o.phone})
                </option>
              ))}
            </select>
          </label>
        )}
        {ownerId && !searching && pets.length === 0 && (
          <p className="text-sm">This owner has no pets. Add one from the owner page.</p>
        )}
        {pets.length > 0 && (
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
        )}
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
          onClick={book}
          disabled={booking || !slot || !petId}
          className="rounded bg-blue-600 px-4 py-2 text-white disabled:opacity-50"
        >
          {booking ? "Booking..." : "Book"}
        </button>
      </div>
    </div>
  );
}
