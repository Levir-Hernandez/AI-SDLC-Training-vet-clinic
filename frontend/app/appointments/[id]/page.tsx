import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { AppointmentActions } from "@/components/AppointmentActions";
import { ErrorMessage } from "@/components/ErrorMessage";
import { ObservationsEditor } from "@/components/ObservationsEditor";
import { StatusBadge } from "@/components/StatusBadge";
import { ApiError, apiGet } from "@/lib/api";
import { getCurrentUser } from "@/lib/auth";
import { breedLabel, SPECIES_LABELS } from "@/lib/breeds";
import type { Appointment } from "@/lib/types";

export const instant = false;

const BOOKER: Record<Appointment["bookedBy"], string> = {
  OWNER: "Owner (online)",
  RECEPTIONIST: "Reception",
  VETERINARIAN: "Veterinarian",
  SYSTEM: "System",
};

function format(iso: string): string {
  return `${iso.slice(0, 10)} ${iso.slice(11, 16)}`;
}

export default async function AppointmentDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const user = await getCurrentUser();
  if (!user) redirect("/login");
  const { id } = await params;

  let a: Appointment | null = null;
  try {
    a = await apiGet<Appointment>(`/api/appointments/${encodeURIComponent(id)}`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    a = null;
  }
  if (a === null) {
    return (
      <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8">
        <ErrorMessage message="Could not load the appointment. Try again later." />
      </main>
    );
  }

  const rows: [string, string][] = [
    ["Pet", `${a.pet.name} (${SPECIES_LABELS[a.pet.species]}, ${breedLabel(a.pet.breed)})`],
    ["Owner", a.ownerName],
    ["Service", a.service.name],
    ["Veterinarian", a.veterinarian.name],
    ["Time", format(a.startAt)],
    ["Duration", `${a.service.durationMinutes} min`],
    ["Total", `$${Number(a.total).toFixed(2)}`],
    ["Booked by", BOOKER[a.bookedBy]],
  ];

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-6 px-4 py-8">
      <div className="flex items-center gap-3">
        <h1 className="text-2xl font-semibold">Appointment #{a.id}</h1>
        <StatusBadge status={a.status} debt={a.debt} />
      </div>
      <dl className="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-2 text-sm">
        {rows.map(([label, value]) => (
          <div key={label} className="contents">
            <dt className="font-medium">{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      {(a.rescheduledFromId != null || a.rescheduledToId != null) && (
        <p className="flex gap-4 text-sm">
          {a.rescheduledFromId != null && (
            <Link href={`/appointments/${a.rescheduledFromId}`} className="underline">
              Rescheduled from #{a.rescheduledFromId}
            </Link>
          )}
          {a.rescheduledToId != null && (
            <Link href={`/appointments/${a.rescheduledToId}`} className="underline">
              Rescheduled to #{a.rescheduledToId}
            </Link>
          )}
        </p>
      )}
      <section className="flex flex-col gap-1">
        <h2 className="text-lg font-semibold">Observations</h2>
        {a.allowedActions.includes("EDIT_OBSERVATIONS") ? (
          <ObservationsEditor appointmentId={a.id} initial={a.observations} />
        ) : (
          <p className="text-sm">{a.observations ?? "No observations recorded."}</p>
        )}
      </section>
      <AppointmentActions
        appointmentId={a.id}
        allowedActions={a.allowedActions}
        total={a.total}
        status={a.status}
      />
    </main>
  );
}
