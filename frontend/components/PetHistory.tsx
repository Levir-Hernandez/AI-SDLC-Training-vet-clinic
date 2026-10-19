import Link from "next/link";
import { EmptyState } from "@/components/EmptyState";
import { StatusBadge } from "@/components/StatusBadge";
import { breedLabel, speciesLabel } from "@/lib/breeds";
import type { Appointment } from "@/lib/types";

const dateTime = (iso: string) =>
  new Intl.DateTimeFormat("en", { dateStyle: "medium", timeStyle: "short", hour12: false }).format(new Date(iso));

function Section({ title, items, showDebt, empty }: { title: string; items: Appointment[]; showDebt: boolean; empty: string }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-xl font-semibold">{title}</h2>
      {items.length === 0 ? (
        <EmptyState message={empty} />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-zinc-200 dark:border-zinc-800">
                <th className="py-2 pr-4">Date and time</th>
                <th className="py-2 pr-4">Service</th>
                <th className="py-2 pr-4">Veterinarian</th>
                <th className="py-2 pr-4">Duration</th>
                <th className="py-2 pr-4">Total</th>
                <th className="py-2 pr-4">Status</th>
                <th className="py-2" />
              </tr>
            </thead>
            <tbody>
              {items.map((a) => (
                <tr key={a.id} className="border-b border-zinc-100 dark:border-zinc-900">
                  <td className="py-2 pr-4">{dateTime(a.startAt)}</td>
                  <td className="py-2 pr-4">{a.service.name}</td>
                  <td className="py-2 pr-4">{a.veterinarian.name}</td>
                  <td className="py-2 pr-4">{a.service.durationMinutes} min</td>
                  <td className="py-2 pr-4">{a.total.toFixed(2)}</td>
                  <td className="py-2 pr-4">
                    <StatusBadge status={a.status} debt={showDebt ? a.debt : false} />
                  </td>
                  <td className="py-2">
                    <Link href={`/appointments/${a.id}`} className="underline">
                      Details
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export function PetHistory({ appointments, showDebt }: { appointments: Appointment[]; showDebt: boolean }) {
  const pet = appointments[0]?.pet;
  return (
    <>
      {pet && (
        <p className="text-sm text-zinc-600 dark:text-zinc-400">
          {pet.name} - {speciesLabel(pet.species)}, {breedLabel(pet.breed)}
        </p>
      )}
      <Section
        title="Upcoming"
        items={appointments.filter((a) => a.upcoming)}
        showDebt={showDebt}
        empty="No upcoming appointments."
      />
      <Section
        title="Past"
        items={appointments.filter((a) => !a.upcoming)}
        showDebt={showDebt}
        empty="No past appointments."
      />
    </>
  );
}
