import Link from "next/link";
import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Pagination } from "@/components/Pagination";
import { StatusBadge } from "@/components/StatusBadge";
import { apiGet } from "@/lib/api";
import type { Appointment, PageResponse } from "@/lib/types";

export const instant = false;

export default async function DebtsPage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string }>;
}) {
  const { page: rawPage } = await searchParams;
  const page = Math.max(0, Number.parseInt(rawPage ?? "0", 10) || 0);

  let data: PageResponse<Appointment> | null = null;
  try {
    data = await apiGet<PageResponse<Appointment>>(
      `/api/appointments?debt=true&page=${page}&size=10`,
    );
  } catch {
    data = null;
  }

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-4 px-4 py-8">
      <h1 className="text-2xl font-semibold">Debts</h1>
      {data === null ? (
        <ErrorMessage message="Could not load debts. Try again later." />
      ) : data.content.length === 0 ? (
        <EmptyState message="No outstanding debts." />
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
                  <th className="py-2 pr-4">Total</th>
                  <th className="py-2 pr-4">Status</th>
                  <th className="py-2">Details</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((a) => (
                  <tr key={a.id} className="border-b border-zinc-200 dark:border-zinc-800">
                    <td className="py-2 pr-4">{a.startAt.replace("T", " ").slice(0, 16)}</td>
                    <td className="py-2 pr-4">{a.pet.name}</td>
                    <td className="py-2 pr-4">{a.ownerName}</td>
                    <td className="py-2 pr-4">{a.service.name}</td>
                    <td className="py-2 pr-4">${Number(a.total).toFixed(2)}</td>
                    <td className="py-2 pr-4">
                      <StatusBadge status={a.status} debt={a.debt} />
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
          <Pagination
            basePath="/reception/debts"
            page={data.page}
            totalPages={Math.ceil(data.totalElements / data.size)}
            params={{}}
          />
        </>
      )}
    </main>
  );
}
