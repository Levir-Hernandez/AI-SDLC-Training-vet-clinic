import { notFound } from "next/navigation";
import { Suspense } from "react";
import { confirmFromDetail } from "@/app/actions/appointments";
import { AppointmentActions } from "@/components/AppointmentActions";
import { ErrorMessage } from "@/components/ErrorMessage";
import { StatusBadge } from "@/components/StatusBadge";
import { ApiError, apiGet } from "@/lib/api";
import { breedLabel, speciesLabel } from "@/lib/breeds";
import type { Appointment } from "@/lib/types";

export const metadata = { title: "Appointment - Vet Clinic" };

const BOOKER: Record<Appointment["bookedBy"], string> = {
  OWNER: "Owner",
  RECEPTIONIST: "Reception",
  VETERINARIAN: "Veterinarian",
  SYSTEM: "System",
};

const dateTime = (iso: string) =>
  new Intl.DateTimeFormat("en", { dateStyle: "medium", timeStyle: "short", hour12: false }).format(new Date(iso));

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1 sm:flex-row sm:gap-4">
      <dt className="w-40 text-sm text-zinc-600 dark:text-zinc-400">{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

async function AppointmentDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!/^\d+$/.test(id)) notFound();

  let a: Appointment;
  try {
    a = await apiGet<Appointment>(`/api/appointments/${id}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 404) notFound();
      return <ErrorMessage message={error.message} />;
    }
    throw error;
  }

  return (
    <>
      <div className="flex items-center gap-4">
        <h1 className="text-2xl font-semibold">Appointment #{a.id}</h1>
        <StatusBadge status={a.status} />
      </div>
      <dl className="flex flex-col gap-3">
        <Row label="Pet">
          {a.pet.name} ({speciesLabel(a.pet.species)}, {breedLabel(a.pet.breed)})
        </Row>
        <Row label="Owner">{a.ownerName}</Row>
        <Row label="Service">{a.service.name}</Row>
        <Row label="Veterinarian">{a.veterinarian.name}</Row>
        <Row label="Date and time">{dateTime(a.startAt)}</Row>
        <Row label="Duration">{a.service.durationMinutes} min</Row>
        <Row label="Total">{a.total.toFixed(2)}</Row>
        <Row label="Booked by">{BOOKER[a.bookedBy]}</Row>
        <Row label="Observations">{a.observations || "None"}</Row>
        {a.cancelReason ? <Row label="Cancel reason">{a.cancelReason}</Row> : null}
      </dl>
      <AppointmentActions actions={a.allowedActions} appointmentId={a.id} handlers={{ CONFIRM: confirmFromDetail.bind(null, a.id) }} />
    </>
  );
}

export default function AppointmentPage({ params }: PageProps<"/appointments/[id]">) {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-6 px-4 py-8">
      <Suspense fallback={<p className="text-zinc-500">Loading appointment...</p>}>
        <AppointmentDetail params={params} />
      </Suspense>
    </main>
  );
}
