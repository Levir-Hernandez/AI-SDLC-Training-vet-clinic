import Link from "next/link";
import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Pagination } from "@/components/Pagination";
import { apiGet } from "@/lib/api";
import { requireRole } from "@/lib/auth";
import { breedLabel, SPECIES_LABELS } from "@/lib/breeds";
import type { PageResponse, Pet } from "@/lib/types";

export const instant = false;

export default async function MyPetsPage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string }>;
}) {
  await requireRole("OWNER");
  const { page: rawPage } = await searchParams;
  const page = Math.max(0, Number.parseInt(rawPage ?? "0", 10) || 0);

  let data: PageResponse<Pet> | null = null;
  try {
    data = await apiGet<PageResponse<Pet>>(`/api/pets?page=${page}&size=10`);
  } catch {
    data = null;
  }

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-4 px-4 py-8">
      <h1 className="text-2xl font-semibold">My pets</h1>
      {data === null ? (
        <ErrorMessage message="Could not load your pets. Try again later." />
      ) : data.content.length === 0 ? (
        <EmptyState message="You have no pets registered yet." />
      ) : (
        <>
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-zinc-300 dark:border-zinc-700">
                <th className="py-2 pr-4">Name</th>
                <th className="py-2 pr-4">Species</th>
                <th className="py-2 pr-4">Breed</th>
                <th className="py-2">History</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((p) => (
                <tr key={p.id} className="border-b border-zinc-200 dark:border-zinc-800">
                  <td className="py-2 pr-4">{p.name}</td>
                  <td className="py-2 pr-4">{SPECIES_LABELS[p.species]}</td>
                  <td className="py-2 pr-4">{breedLabel(p.breed)}</td>
                  <td className="py-2">
                    <Link href={`/owner/pets/${p.id}/history`} className="underline">
                      History
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination
            basePath="/owner/pets"
            page={data.page}
            totalPages={Math.ceil(data.totalElements / data.size)}
          />
        </>
      )}
    </main>
  );
}
