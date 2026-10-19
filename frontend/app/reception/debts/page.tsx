import Link from "next/link";
import { Suspense } from "react";
import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Pagination } from "@/components/Pagination";
import { StatusBadge } from "@/components/StatusBadge";
import { ApiError, apiGet } from "@/lib/api";
import type { Appointment, PageResponse } from "@/lib/types";

export const metadata = { title: "Debts - Vet Clinic" };

const dateTime = (iso: string) =>
  new Intl.DateTimeFormat("en", { dateStyle: "medium", timeStyle: "short", hour12: false }).format(new Date(iso));

async function DebtList({ searchParams }: { searchParams: Promise<{ page?: string }> }) {
  const params = await searchParams;
  const page = /^\d+$/.test(params.page ?? "") ? Number(params.page) : 0;

  let data: PageResponse<Appointment>;
  try {
    data = await apiGet<PageResponse<Appointment>>(`/api/appointments?debt=true&page=${page}`);
  } catch (error) {
    if (error instanceof ApiError) return <ErrorMessage message={error.message} />;
    throw error;
  }

  if (data.content.length === 0) return <EmptyState message="No debts." />;

  return (
    <>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-zinc-200 dark:border-zinc-800">
              <th className="py-2 pr-4">Date and time</th>
              <th className="py-2 pr-4">Pet</th>
              <th className="py-2 pr-4">Owner</th>
              <th className="py-2 pr-4">Service</th>
              <th className="py-2 pr-4">Total</th>
              <th className="py-2 pr-4">Status</th>
              <th className="py-2" />
            </tr>
          </thead>
          <tbody>
            {data.content.map((a) => (
              <tr key={a.id} className="border-b border-zinc-100 dark:border-zinc-900">
                <td className="py-2 pr-4">{dateTime(a.startAt)}</td>
                <td className="py-2 pr-4">{a.pet.name}</td>
                <td className="py-2 pr-4">{a.ownerName}</td>
                <td className="py-2 pr-4">{a.service.name}</td>
                <td className="py-2 pr-4">{a.total.toFixed(2)}</td>
                <td className="py-2 pr-4">
                  <StatusBadge status={a.status} debt={a.debt} />
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
      <Pagination basePath="/reception/debts" page={data.page} size={data.size} totalElements={data.totalElements} />
    </>
  );
}

export default function DebtsPage({ searchParams }: PageProps<"/reception/debts">) {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-4 px-4 py-8">
      <h1 className="text-2xl font-semibold">Debts</h1>
      <Suspense fallback={<p className="text-zinc-500">Loading debts...</p>}>
        <DebtList searchParams={searchParams} />
      </Suspense>
    </main>
  );
}
