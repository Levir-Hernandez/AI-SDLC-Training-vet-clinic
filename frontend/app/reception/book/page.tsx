import Link from "next/link";
import { Suspense } from "react";
import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { ApiError, apiGet } from "@/lib/api";
import type { Owner, PageResponse, Pet, Service, Veterinarian } from "@/lib/types";
import { WalkInForm } from "./walk-in-form";

export const metadata = { title: "Book walk-in - Vet Clinic" };

type Loaded =
  | { kind: "error"; message: string }
  | { kind: "form"; pets: Pet[]; services: Service[]; vets: Veterinarian[] }
  | { kind: "owners"; owners: Owner[] };

async function load(q: string, ownerId: string): Promise<Loaded | null> {
  try {
    if (ownerId) {
      const [pets, services, vets] = await Promise.all([
        apiGet<PageResponse<Pet>>(`/api/pets?ownerId=${ownerId}&size=100`),
        apiGet<Service[]>("/api/services"),
        apiGet<PageResponse<Veterinarian>>("/api/veterinarians?size=100"),
      ]);
      return { kind: "form", pets: pets.content, services, vets: vets.content };
    }
    if (!q) return null;
    const owners = await apiGet<PageResponse<Owner>>(`/api/owners?q=${encodeURIComponent(q)}&size=10`);
    return { kind: "owners", owners: owners.content };
  } catch (error) {
    if (error instanceof ApiError) return { kind: "error", message: error.message };
    throw error;
  }
}

async function OwnerResults({ q, ownerId }: { q: string; ownerId: string }) {
  const data = await load(q, ownerId);
  if (!data) return <p className="text-sm text-zinc-500">Search for the owner to start.</p>;
  if (data.kind === "error") return <ErrorMessage message={data.message} />;
  if (data.kind === "form") {
    if (data.pets.length === 0) {
      return <EmptyState message="This owner has no pets yet. Add a pet first." />;
    }
    return <WalkInForm pets={data.pets} services={data.services} veterinarians={data.vets} />;
  }
  if (data.owners.length === 0) return <EmptyState message="No owners match your search." />;
  return (
    <ul className="flex flex-col gap-2">
      {data.owners.map((o) => (
        <li key={o.id}>
          <Link href={`/reception/book?ownerId=${o.id}&q=${encodeURIComponent(q)}`} className="underline">
            {o.name} ({o.phone})
          </Link>
        </li>
      ))}
    </ul>
  );
}

async function BookContent({ searchParams }: { searchParams: Promise<{ q?: string; ownerId?: string }> }) {
  const { q, ownerId } = await searchParams;
  const owner = /^\d+$/.test(ownerId ?? "") ? (ownerId as string) : "";
  return (
    <>
      <form method="get" role="search" className="flex gap-2">
        <input
          type="search"
          name="q"
          defaultValue={q ?? ""}
          aria-label="Search owner by name or phone"
          placeholder="Search owner by name or phone"
          className="flex-1 rounded border border-zinc-300 bg-transparent px-3 py-2 dark:border-zinc-700"
        />
        <button type="submit" className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700">
          Search
        </button>
      </form>
      <Suspense fallback={<p className="text-zinc-500">Loading...</p>}>
        <OwnerResults q={q?.trim() ?? ""} ownerId={owner} />
      </Suspense>
    </>
  );
}

export default function BookWalkInPage({ searchParams }: PageProps<"/reception/book">) {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-6 px-4 py-8">
      <h1 className="text-2xl font-semibold">Book a walk-in</h1>
      <Suspense fallback={<p className="text-zinc-500">Loading...</p>}>
        <BookContent searchParams={searchParams} />
      </Suspense>
    </main>
  );
}
