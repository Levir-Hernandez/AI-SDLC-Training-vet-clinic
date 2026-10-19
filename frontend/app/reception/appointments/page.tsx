import Link from "next/link";
import { Suspense } from "react";
import { closeAppointment, finishAppointment, startAppointment } from "@/app/actions/appointments";
import { AppointmentActions } from "@/components/AppointmentActions";
import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Pagination } from "@/components/Pagination";
import { StatusBadge } from "@/components/StatusBadge";
import { ApiError, apiGet } from "@/lib/api";
import type { Appointment, PageResponse, Veterinarian } from "@/lib/types";

export const metadata = { title: "Ongoing appointments - Vet Clinic" };

type Params = { page?: string; date?: string; veterinarianId?: string };

const dateTime = (iso: string) =>
  new Intl.DateTimeFormat("en", { dateStyle: "medium", timeStyle: "short", hour12: false }).format(new Date(iso));

async function Board({ searchParams }: { searchParams: Promise<Params> }) {
  const params = await searchParams;
  const page = /^\d+$/.test(params.page ?? "") ? Number(params.page) : 0;
  const date = /^\d{4}-\d{2}-\d{2}$/.test(params.date ?? "") ? params.date! : "";
  const vetId = /^\d+$/.test(params.veterinarianId ?? "") ? params.veterinarianId! : "";

  const query = new URLSearchParams({ ongoing: "true", page: String(page) });
  if (date) query.set("date", date);
  if (vetId) query.set("veterinarianId", vetId);

  let data: PageResponse<Appointment>;
  let vets: PageResponse<Veterinarian>;
  try {
    [data, vets] = await Promise.all([
      apiGet<PageResponse<Appointment>>(`/api/appointments?${query}`),
      apiGet<PageResponse<Veterinarian>>("/api/veterinarians?size=100"),
    ]);
  } catch (error) {
    if (error instanceof ApiError) return <ErrorMessage message={error.message} />;
    throw error;
  }

  const keep: Record<string, string> = {};
  if (date) keep.date = date;
  if (vetId) keep.veterinarianId = vetId;

  return (
    <>
      <form method="get" className="flex flex-wrap items-end gap-3 text-sm">
        <label className="flex flex-col gap-1">
          Date
          <input
            type="date"
            name="date"
            defaultValue={date}
            className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-950"
          />
        </label>
        <label className="flex flex-col gap-1">
          Veterinarian
          <select
            name="veterinarianId"
            defaultValue={vetId}
            className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-950"
          >
            <option value="">All veterinarians</option>
            {vets.content.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700">
          Filter
        </button>
        <Link href="/reception/appointments" className="px-2 py-2 underline">
          Clear
        </Link>
      </form>

      {data.content.length === 0 ? (
        <EmptyState message="No ongoing appointments." />
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-zinc-200 dark:border-zinc-800">
                  <th className="py-2 pr-4">Date and time</th>
                  <th className="py-2 pr-4">Pet</th>
                  <th className="py-2 pr-4">Owner</th>
                  <th className="py-2 pr-4">Service</th>
                  <th className="py-2 pr-4">Veterinarian</th>
                  <th className="py-2 pr-4">Status</th>
                  <th className="py-2 pr-4">Actions</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody>
                {data.content.map((a) => (
                  <tr key={a.id} className="border-b border-zinc-100 align-top dark:border-zinc-900">
                    <td className="py-2 pr-4">{dateTime(a.startAt)}</td>
                    <td className="py-2 pr-4">{a.pet.name}</td>
                    <td className="py-2 pr-4">{a.ownerName}</td>
                    <td className="py-2 pr-4">{a.service.name}</td>
                    <td className="py-2 pr-4">{a.veterinarian.name}</td>
                    <td className="py-2 pr-4">
                      <StatusBadge status={a.status} debt={a.debt} />
                    </td>
                    <td className="py-2 pr-4">
                      <AppointmentActions
                        actions={a.allowedActions}
                        appointmentId={a.id}
                        total={a.total}
                        status={a.status}
                        handlers={{
                          CLOSE: closeAppointment.bind(null, a.id),
                          START: startAppointment.bind(null, a.id),
                          FINISH: finishAppointment.bind(null, a.id),
                        }}
                      />
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
          <Pagination
            basePath="/reception/appointments"
            query={keep}
            page={data.page}
            size={data.size}
            totalElements={data.totalElements}
          />
        </>
      )}
    </>
  );
}

export default function ReceptionAppointmentsPage({ searchParams }: PageProps<"/reception/appointments">) {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-4 px-4 py-8">
      <h1 className="text-2xl font-semibold">Ongoing appointments</h1>
      <Suspense fallback={<p className="text-zinc-500">Loading appointments...</p>}>
        <Board searchParams={searchParams} />
      </Suspense>
    </main>
  );
}
