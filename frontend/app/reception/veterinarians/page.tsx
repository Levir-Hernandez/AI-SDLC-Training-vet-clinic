import { Suspense } from "react";
import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Pagination } from "@/components/Pagination";
import { ApiError, apiGet } from "@/lib/api";
import type { PageResponse, Veterinarian } from "@/lib/types";
import { VeterinarianForm } from "./veterinarian-form";

export const metadata = { title: "Veterinarians - Vet Clinic" };

const DAY_ABBR: Record<string, string> = {
  MONDAY: "Mon",
  TUESDAY: "Tue",
  WEDNESDAY: "Wed",
  THURSDAY: "Thu",
  FRIDAY: "Fri",
  SATURDAY: "Sat",
  SUNDAY: "Sun",
};

const hhmm = (t: string) => t.slice(0, 5);

function scheduleSummary(vet: Veterinarian): string {
  return vet.schedule
    .map((b) => `${DAY_ABBR[b.day] ?? b.day} ${hhmm(b.start)}-${hhmm(b.end)}`)
    .join(", ");
}

async function VeterinarianList({ searchParams }: { searchParams: Promise<{ page?: string }> }) {
  const { page } = await searchParams;
  const pageNumber = Math.max(0, Number.parseInt(page ?? "0", 10) || 0);

  let data: PageResponse<Veterinarian>;
  try {
    data = await apiGet<PageResponse<Veterinarian>>(`/api/veterinarians?page=${pageNumber}`);
  } catch (error) {
    if (error instanceof ApiError) return <ErrorMessage message={error.message} />;
    throw error;
  }

  if (data.content.length === 0) return <EmptyState message="No veterinarians registered yet." />;

  return (
    <div className="flex flex-col gap-4">
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-zinc-200 dark:border-zinc-800">
              <th className="py-2 pr-4">Name</th>
              <th className="py-2 pr-4">Specialization</th>
              <th className="py-2 pr-4">Cost per service</th>
              <th className="py-2">Schedule</th>
            </tr>
          </thead>
          <tbody>
            {data.content.map((vet) => (
              <tr key={vet.id} className="border-b border-zinc-100 align-top dark:border-zinc-900">
                <td className="py-2 pr-4">{vet.name}</td>
                <td className="py-2 pr-4">{vet.specialization}</td>
                <td className="py-2 pr-4">{vet.costPerService.toFixed(2)}</td>
                <td className="py-2">{scheduleSummary(vet)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination
        basePath="/reception/veterinarians"
        page={data.page}
        size={data.size}
        totalElements={data.totalElements}
      />
    </div>
  );
}

export default function VeterinariansPage({ searchParams }: PageProps<"/reception/veterinarians">) {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-8 px-4 py-8">
      <section className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">Veterinarians</h1>
        <Suspense fallback={<p className="text-zinc-500">Loading veterinarians...</p>}>
          <VeterinarianList searchParams={searchParams} />
        </Suspense>
      </section>
      <VeterinarianForm />
    </main>
  );
}
