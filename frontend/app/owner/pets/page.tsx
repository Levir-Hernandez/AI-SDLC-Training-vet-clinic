import Link from "next/link";
import { Suspense } from "react";
import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { ApiError, apiGet } from "@/lib/api";
import { breedLabel, speciesLabel } from "@/lib/breeds";
import type { PageResponse, Pet } from "@/lib/types";

export const metadata = { title: "My pets - Vet Clinic" };

async function PetList() {
  let data: PageResponse<Pet>;
  try {
    data = await apiGet<PageResponse<Pet>>("/api/pets");
  } catch (error) {
    if (error instanceof ApiError) return <ErrorMessage message={error.message} />;
    throw error;
  }

  if (data.content.length === 0) return <EmptyState message="You have no pets registered yet." />;

  return (
    <table className="w-full text-left text-sm">
      <thead>
        <tr className="border-b border-zinc-200 dark:border-zinc-800">
          <th className="py-2 pr-4">Name</th>
          <th className="py-2 pr-4">Species</th>
          <th className="py-2 pr-4">Breed</th>
          <th className="py-2" />
        </tr>
      </thead>
      <tbody>
        {data.content.map((pet) => (
          <tr key={pet.id} className="border-b border-zinc-100 dark:border-zinc-900">
            <td className="py-2 pr-4">{pet.name}</td>
            <td className="py-2 pr-4">{speciesLabel(pet.species)}</td>
            <td className="py-2 pr-4">{breedLabel(pet.breed)}</td>
            <td className="py-2">
              <Link href={`/owner/pets/${pet.id}/history`} className="underline">
                History
              </Link>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export default function OwnerPetsPage() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-4 px-4 py-8">
      <h1 className="text-2xl font-semibold">My pets</h1>
      <Suspense fallback={<p className="text-zinc-500">Loading pets...</p>}>
        <PetList />
      </Suspense>
    </main>
  );
}
