import Link from "next/link";
import { AppointmentActions } from "@/components/AppointmentActions";
import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Pagination } from "@/components/Pagination";
import { StatusBadge } from "@/components/StatusBadge";
import { apiGet } from "@/lib/api";
import { requireRole } from "@/lib/auth";
import type { Appointment, PageResponse, Veterinarian } from "@/lib/types";

export const instant = false;

export default async function ReceptionAppointmentsPage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string; date?: string; veterinarianId?: string }>;
}) {
  await requireRole("RECEPTIONIST");
  const { page: rawPage, date = "", veterinarianId = "" } = await searchParams;
  const page = Math.max(0, Number.parseInt(rawPage ?? "0", 10) || 0);

  const query = new URLSearchParams({ ongoing: "true", page: String(page), size: "10" });
  if (/^\d{4}-\d{2}-\d{2}$/.test(date)) query.set("date", date);
  if (/^\d+$/.test(veterinarianId)) query.set("veterinarianId", veterinarianId);

  let vets: Veterinarian[] = [];
  try {
    vets = (await apiGet<PageResponse<Veterinarian>>("/api/veterinarians?size=100")).content;
  } catch {
    vets = [];
  }

  let data: PageResponse<Appointment> | null = null;
  try {
    data = await apiGet<PageResponse<Appointment>>(`/api/appointments?${query.toString()}`);
  } catch {
    data = null;
  }

  const params: Record<string, string> = {};
  if (query.has("date")) params.date = query.get("date")!;
  if (query.has("veterinarianId")) params.veterinarianId = query.get("veterinarianId")!;

  const inputClass =
    "rounded border border-zinc-300 px-2 py-1 text-sm dark:border-zinc-700 dark:bg-transparent";

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-4 px-4 py-8">
      <h1 className="text-2xl font-semibold">Ongoing appointments</h1>
      <form method="get" className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-sm">
          Date
          <input type="date" name="date" defaultValue={params.date ?? ""} className={inputClass} />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Veterinarian
          <select
            name="veterinarianId"
            defaultValue={params.veterinarianId ?? ""}
            className={inputClass}
          >
            <option value="">All</option>
            {vets.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="rounded border border-zinc-300 px-3 py-1 text-sm dark:border-zinc-700">
          Filter
        </button>
      </form>
      {data === null ? (
        <ErrorMessage message="Could not load appointments. Try again later." />
      ) : data.content.length === 0 ? (
        <EmptyState message="No ongoing appointments." />
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-zinc-300 dark:border-zinc-700">
                  <th className="py-2 pr-4">Date and time</th>
                  <th className="py-2 pr-4">Pet</th>
                  <th className="py-2 pr-4">Owner</th>
                  <th className="py-2 pr-4">Service</th>
                  <th className="py-2 pr-4">Veterinarian</th>
                  <th className="py-2 pr-4">Status</th>
                  <th className="py-2">Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((a) => (
                  <tr key={a.id} className="border-b border-zinc-200 dark:border-zinc-800">
                    <td className="py-2 pr-4">{a.startAt.replace("T", " ").slice(0, 16)}</td>
                    <td className="py-2 pr-4">
                      <Link href={`/reception/pets/${a.pet.id}/history`} className="underline">
                        {a.pet.name}
                      </Link>
                    </td>
                    <td className="py-2 pr-4">{a.ownerName}</td>
                    <td className="py-2 pr-4">{a.service.name}</td>
                    <td className="py-2 pr-4">{a.veterinarian.name}</td>
                    <td className="py-2 pr-4">
                      <StatusBadge status={a.status} debt={a.debt} />
                    </td>
                    <td className="py-2">
                      <AppointmentActions
                        appointmentId={a.id}
                        allowedActions={a.allowedActions}
                        total={a.total}
                        status={a.status}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            basePath="/reception/appointments"
            page={data.page}
            totalPages={Math.ceil(data.totalElements / data.size)}
            params={params}
          />
        </>
      )}
    </main>
  );
}
