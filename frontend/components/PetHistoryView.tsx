import Link from "next/link";
import { EmptyState } from "@/components/EmptyState";
import { StatusBadge } from "@/components/StatusBadge";
import type { Appointment } from "@/lib/types";

function Section({
  title,
  items,
  emptyMessage,
  showDebt,
}: {
  title: string;
  items: Appointment[];
  emptyMessage: string;
  showDebt: boolean;
}) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-xl font-semibold">{title}</h2>
      {items.length === 0 ? (
        <EmptyState message={emptyMessage} />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-zinc-300 dark:border-zinc-700">
                <th className="py-2 pr-4">Date and time</th>
                <th className="py-2 pr-4">Service</th>
                <th className="py-2 pr-4">Veterinarian</th>
                <th className="py-2 pr-4">Duration</th>
                <th className="py-2 pr-4">Total</th>
                <th className="py-2 pr-4">Status</th>
                <th className="py-2">Details</th>
              </tr>
            </thead>
            <tbody>
              {items.map((a) => (
                <tr key={a.id} className="border-b border-zinc-200 dark:border-zinc-800">
                  <td className="py-2 pr-4">{a.startAt.replace("T", " ").slice(0, 16)}</td>
                  <td className="py-2 pr-4">{a.service.name}</td>
                  <td className="py-2 pr-4">{a.veterinarian.name}</td>
                  <td className="py-2 pr-4">{a.service.durationMinutes} min</td>
                  <td className="py-2 pr-4">{Number(a.total).toFixed(2)}</td>
                  <td className="py-2 pr-4">
                    <StatusBadge status={a.status} debt={showDebt ? a.debt : false} />
                  </td>
                  <td className="py-2">
                    <Link href={`/appointments/${a.id}`} className="underline">
                      View
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

export function PetHistoryView({
  items,
  showDebt,
}: {
  items: Appointment[];
  showDebt: boolean;
}) {
  const upcoming = items.filter((a) => a.upcoming);
  const past = items.filter((a) => !a.upcoming);
  const pet = items[0]?.pet;
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-8 px-4 py-8">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold">{pet ? `${pet.name} - service history` : "Service history"}</h1>
        {items[0] ? <p className="text-sm">Owner: {items[0].ownerName}</p> : null}
      </header>
      <Section title="Upcoming" items={upcoming} emptyMessage="No upcoming appointments." showDebt={showDebt} />
      <Section title="Past" items={past} emptyMessage="No past appointments." showDebt={showDebt} />
    </main>
  );
}
