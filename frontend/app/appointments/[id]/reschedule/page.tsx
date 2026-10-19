import { notFound, redirect } from "next/navigation";
import { Suspense } from "react";
import { ErrorMessage } from "@/components/ErrorMessage";
import { ApiError, apiGet } from "@/lib/api";
import { requireRole } from "@/lib/auth";
import type { Appointment, PageResponse, Service, Veterinarian } from "@/lib/types";
import { RescheduleForm } from "./reschedule-form";

export const metadata = { title: "Reschedule appointment - Vet Clinic" };

async function RescheduleContent({ params }: { params: Promise<{ id: string }> }) {
  await requireRole("RECEPTIONIST");
  const { id } = await params;
  if (!/^\d+$/.test(id)) notFound();

  let appointment: Appointment;
  let services: Service[];
  let vets: PageResponse<Veterinarian>;
  try {
    [appointment, services, vets] = await Promise.all([
      apiGet<Appointment>(`/api/appointments/${id}`),
      apiGet<Service[]>("/api/services"),
      apiGet<PageResponse<Veterinarian>>("/api/veterinarians?size=100"),
    ]);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 404) notFound();
      return <ErrorMessage message={error.message} />;
    }
    throw error;
  }
  if (!appointment.allowedActions.includes("RESCHEDULE")) redirect(`/appointments/${id}`);

  return (
    <>
      <h1 className="text-2xl font-semibold">Reschedule appointment #{appointment.id}</h1>
      <p className="text-sm text-zinc-600 dark:text-zinc-400">
        {appointment.pet.name} - {appointment.service.name} with {appointment.veterinarian.name}. Pick a new time
        and, if needed, another veterinarian.
      </p>
      <RescheduleForm
        appointmentId={appointment.id}
        services={services.filter((s) => s.id === appointment.service.id)}
        serviceId={appointment.service.id}
        veterinarians={vets.content}
      />
    </>
  );
}

export default function ReschedulePage({ params }: PageProps<"/appointments/[id]/reschedule">) {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-6 px-4 py-8">
      <Suspense fallback={<p className="text-zinc-500">Loading...</p>}>
        <RescheduleContent params={params} />
      </Suspense>
    </main>
  );
}
