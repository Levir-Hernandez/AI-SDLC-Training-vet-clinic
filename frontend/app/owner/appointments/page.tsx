import Link from "next/link";
import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Pagination } from "@/components/Pagination";
import { StatusBadge } from "@/components/StatusBadge";
import { apiGet } from "@/lib/api";
import { requireRole } from "@/lib/auth";
import type { Appointment, PageResponse } from "@/lib/types";

export const instant = false;

export default async function OwnerAppointmentsPage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string }>;
}) {
  await requireRole("OWNER");
  const { page: rawPage } = await searchParams;
  const page = Math.max(0, Number.parseInt(rawPage ?? "0", 10) || 0);

  let data: PageResponse<Appointment> | null = null;
  try {
    data = await apiGet<PageResponse<Appointment>>(`/api/appointments?page=${page}&size=10`);
  } catch {
    data = null;
  }

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-4 px-4 py-8">
      <h1 className="text-2xl font-semibold">My appointments</h1>
      {data === null ? (
        <ErrorMessage message="Could not load your appointments. Try again later." />
      ) : data.content.length === 0 ? (
        <EmptyState message="You have no appointments yet." />
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-zinc-300 dark:border-zinc-700">
                  <th className="py-2 pr-4">Pet</th>
                  <th className="py-2 pr-4">Service</th>
                  <th className="py-2 pr-4">Veterinarian</th>
                  <th className="py-2 pr-4">Date and time</th>
                  <th className="py-2 pr-4">Est. duration</th>
                  <th className="py-2 pr-4">Status</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody>
                {data.content.map((a) => (
                  <tr key={a.id} className="border-b border-zinc-200 dark:border-zinc-800">
                    <td className="py-2 pr-4">{a.pet.name}</td>
                    <td className="py-2 pr-4">{a.service.name}</td>
                    <td className="py-2 pr-4">{a.veterinarian.name}</td>
                    <td className="py-2 pr-4">{a.startAt.replace("T", " ").slice(0, 16)}</td>
                    <td className="py-2 pr-4">{a.service.durationMinutes} min</td>
                    <td className="py-2 pr-4">
                      <StatusBadge status={a.status} />
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
            basePath="/owner/appointments"
            page={data.page}
            totalPages={Math.ceil(data.totalElements / data.size)}
          />
        </>
      )}
    </main>
  );
}
