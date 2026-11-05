import { notFound } from "next/navigation";
import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { ApiError, apiGet } from "@/lib/api";
import { breedLabel, SPECIES_LABELS } from "@/lib/breeds";
import { requireRole } from "@/lib/auth";
import type { Owner } from "@/lib/types";
import { AddPetForm } from "./add-pet-form";

export const instant = false;

export default async function OwnerDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  await requireRole("RECEPTIONIST");
  const { id } = await params;

  let owner: Owner | null = null;
  try {
    owner = await apiGet<Owner>(`/api/owners/${encodeURIComponent(id)}`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    owner = null;
  }
  if (owner === null) {
    return (
      <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8">
        <ErrorMessage message="Could not load the owner. Try again later." />
      </main>
    );
  }
  const pets = owner.pets ?? [];

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-8 px-4 py-8">
      <section className="flex flex-col gap-2">
        <h1 className="text-2xl font-semibold">{owner.name}</h1>
        <p className="text-sm">Phone: {owner.phone}</p>
      </section>
      <section className="flex flex-col gap-4">
        <h2 className="text-xl font-semibold">Pets</h2>
        {pets.length === 0 ? (
          <EmptyState message="This owner has no pets yet." />
        ) : (
          <ul className="flex flex-col gap-1">
            {pets.map((p) => (
              <li key={p.id}>
                {p.name} - {SPECIES_LABELS[p.species]}, {breedLabel(p.breed)}
              </li>
            ))}
          </ul>
        )}
      </section>
      <section className="flex flex-col gap-4">
        <h2 className="text-xl font-semibold">Add a pet</h2>
        <AddPetForm ownerId={owner.id} />
      </section>
    </main>
  );
}
