import { notFound } from "next/navigation";
import { ErrorMessage } from "@/components/ErrorMessage";
import { ApiError, apiGet } from "@/lib/api";
import { requireRole } from "@/lib/auth";
import type { Appointment, PageResponse, Service, Veterinarian } from "@/lib/types";
import { RescheduleForm } from "./reschedule-form";

export const instant = false;

export default async function ReschedulePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  await requireRole("RECEPTIONIST");
  const { id } = await params;

  let appointment: Appointment | null = null;
  let services: Service[] | null = null;
  let vets: Veterinarian[] | null = null;
  try {
    appointment = await apiGet<Appointment>(`/api/appointments/${encodeURIComponent(id)}`);
    services = await apiGet<Service[]>("/api/services");
    vets = (await apiGet<PageResponse<Veterinarian>>("/api/veterinarians?size=100")).content;
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    appointment = null;
  }

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-6 px-4 py-8">
      <h1 className="text-2xl font-semibold">Reschedule appointment #{id}</h1>
      {appointment === null || services === null || vets === null ? (
        <ErrorMessage message="Could not load the appointment. Try again later." />
      ) : !appointment.allowedActions.includes("RESCHEDULE") ? (
        <ErrorMessage message="This appointment can no longer be rescheduled." />
      ) : (
        <RescheduleForm
          appointmentId={appointment.id}
          serviceId={appointment.service.id}
          veterinarianId={appointment.veterinarian.id}
          services={services}
          veterinarians={vets}
        />
      )}
    </main>
  );
}
