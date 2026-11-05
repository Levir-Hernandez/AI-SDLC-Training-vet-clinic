import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Pagination } from "@/components/Pagination";
import { apiGet } from "@/lib/api";
import { requireRole } from "@/lib/auth";
import type { PageResponse, ScheduleBlock, Veterinarian } from "@/lib/types";
import { VeterinarianForm } from "./veterinarian-form";

export const instant = false;

function summarize(schedule: ScheduleBlock[]) {
  return schedule
    .map((b) => `${b.day.slice(0, 3)} ${b.start.slice(0, 5)}-${b.end.slice(0, 5)}`)
    .join(", ");
}

export default async function VeterinariansPage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string }>;
}) {
  await requireRole("RECEPTIONIST");
  const { page: rawPage } = await searchParams;
  const page = Math.max(0, Number.parseInt(rawPage ?? "0", 10) || 0);

  let data: PageResponse<Veterinarian> | null = null;
  try {
    data = await apiGet<PageResponse<Veterinarian>>(
      `/api/veterinarians?page=${page}&size=10`,
    );
  } catch {
    data = null;
  }

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-8 px-4 py-8">
      <section className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">Veterinarians</h1>
        {data === null ? (
          <ErrorMessage message="Could not load veterinarians. Try again later." />
        ) : data.content.length === 0 ? (
          <EmptyState message="No veterinarians registered yet." />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-zinc-300 dark:border-zinc-700">
                    <th className="py-2 pr-4">Name</th>
                    <th className="py-2 pr-4">Specialization</th>
                    <th className="py-2 pr-4">Cost</th>
                    <th className="py-2">Schedule</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((v) => (
                    <tr key={v.id} className="border-b border-zinc-200 dark:border-zinc-800">
                      <td className="py-2 pr-4">{v.name}</td>
                      <td className="py-2 pr-4">{v.specialization}</td>
                      <td className="py-2 pr-4">{Number(v.costPerService).toFixed(2)}</td>
                      <td className="py-2">{summarize(v.schedule)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              basePath="/reception/veterinarians"
              page={data.page}
              totalPages={Math.ceil(data.totalElements / data.size)}
            />
          </>
        )}
      </section>
      <section className="flex flex-col gap-4">
        <h2 className="text-xl font-semibold">Register a veterinarian</h2>
        <VeterinarianForm />
      </section>
    </main>
  );
}
