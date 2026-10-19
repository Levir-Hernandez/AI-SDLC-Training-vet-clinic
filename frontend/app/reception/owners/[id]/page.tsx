import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { ApiError, apiGet } from "@/lib/api";
import { breedLabel, speciesLabel } from "@/lib/breeds";
import type { Owner } from "@/lib/types";
import { AddPetForm } from "./add-pet-form";

export const metadata = { title: "Owner - Vet Clinic" };

async function OwnerDetail({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!/^\d+$/.test(id)) notFound();

  let owner: Owner;
  try {
    owner = await apiGet<Owner>(`/api/owners/${id}`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 404) notFound();
      return <ErrorMessage message={error.message} />;
    }
    throw error;
  }

  return (
    <>
      <section className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">{owner.name}</h1>
        <p className="text-sm text-zinc-600 dark:text-zinc-400">Phone: {owner.phone}</p>
        <h2 className="text-xl font-semibold">Pets</h2>
        {owner.pets.length === 0 ? (
          <EmptyState message="This owner has no pets yet." />
        ) : (
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-zinc-200 dark:border-zinc-800">
                <th className="py-2 pr-4">Name</th>
                <th className="py-2 pr-4">Species</th>
                <th className="py-2">Breed</th>
              </tr>
            </thead>
            <tbody>
              {owner.pets.map((pet) => (
                <tr key={pet.id} className="border-b border-zinc-100 dark:border-zinc-900">
                  <td className="py-2 pr-4">{pet.name}</td>
                  <td className="py-2 pr-4">{speciesLabel(pet.species)}</td>
                  <td className="py-2">{breedLabel(pet.breed)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
      <AddPetForm ownerId={owner.id} />
    </>
  );
}

export default function OwnerDetailPage({ params }: PageProps<"/reception/owners/[id]">) {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-8 px-4 py-8">
      <Link href="/reception/owners" className="text-sm underline">
        Back to owners
      </Link>
      <Suspense fallback={<p className="text-zinc-500">Loading owner...</p>}>
        <OwnerDetail params={params} />
      </Suspense>
    </main>
  );
}
