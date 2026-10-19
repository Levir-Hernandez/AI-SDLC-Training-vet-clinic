import Link from "next/link";
import { Suspense } from "react";
import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Pagination } from "@/components/Pagination";
import { ApiError, apiGet } from "@/lib/api";
import type { Owner, PageResponse } from "@/lib/types";
import { OwnerForm } from "./owner-form";

export const metadata = { title: "Owners - Vet Clinic" };

async function OwnerList({
  searchParams,
}: {
  searchParams: Promise<{ page?: string; q?: string }>;
}) {
  const { page, q } = await searchParams;
  const pageNumber = Math.max(0, Number.parseInt(page ?? "0", 10) || 0);
  const query = q?.trim() ?? "";

  let data: PageResponse<Owner>;
  try {
    data = await apiGet<PageResponse<Owner>>(
      `/api/owners?page=${pageNumber}&q=${encodeURIComponent(query)}`,
    );
  } catch (error) {
    if (error instanceof ApiError) return <ErrorMessage message={error.message} />;
    throw error;
  }

  if (data.content.length === 0) {
    return (
      <EmptyState message={query ? "No owners match your search." : "No owners registered yet."} />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-zinc-200 dark:border-zinc-800">
            <th className="py-2 pr-4">Name</th>
            <th className="py-2 pr-4">Phone</th>
            <th className="py-2" />
          </tr>
        </thead>
        <tbody>
          {data.content.map((owner) => (
            <tr key={owner.id} className="border-b border-zinc-100 dark:border-zinc-900">
              <td className="py-2 pr-4">{owner.name}</td>
              <td className="py-2 pr-4">{owner.phone}</td>
              <td className="py-2">
                <Link href={`/reception/owners/${owner.id}`} className="underline">
                  View pets
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <Pagination
        basePath="/reception/owners"
        query={query ? { q: query } : undefined}
        page={data.page}
        size={data.size}
        totalElements={data.totalElements}
      />
    </div>
  );
}

export default function OwnersPage({ searchParams }: PageProps<"/reception/owners">) {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-8 px-4 py-8">
      <section className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">Owners</h1>
        <form method="get" className="flex gap-2" role="search">
          <input
            type="search"
            name="q"
            aria-label="Search by name or phone"
            placeholder="Search by name or phone"
            className="flex-1 rounded border border-zinc-300 bg-transparent px-3 py-2 dark:border-zinc-700"
          />
          <button
            type="submit"
            className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700"
          >
            Search
          </button>
        </form>
        <Suspense fallback={<p className="text-zinc-500">Loading owners...</p>}>
          <OwnerList searchParams={searchParams} />
        </Suspense>
      </section>
      <OwnerForm />
    </main>
  );
}
