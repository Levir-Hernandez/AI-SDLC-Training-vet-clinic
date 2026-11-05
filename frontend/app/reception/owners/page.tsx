import Link from "next/link";
import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { Pagination } from "@/components/Pagination";
import { apiGet } from "@/lib/api";
import { requireRole } from "@/lib/auth";
import type { Owner, PageResponse } from "@/lib/types";
import { OwnerForm } from "./owner-form";

export const instant = false;

export default async function OwnersPage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string; q?: string }>;
}) {
  await requireRole("RECEPTIONIST");
  const { page: rawPage, q: rawQ } = await searchParams;
  const page = Math.max(0, Number.parseInt(rawPage ?? "0", 10) || 0);
  const q = (rawQ ?? "").trim();

  let data: PageResponse<Owner> | null = null;
  try {
    data = await apiGet<PageResponse<Owner>>(
      `/api/owners?page=${page}&size=10&q=${encodeURIComponent(q)}`,
    );
  } catch {
    data = null;
  }

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-8 px-4 py-8">
      <section className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">Owners</h1>
        <form method="get" role="search" className="flex gap-2">
          <input
            name="q"
            defaultValue={q}
            aria-label="Search by name or phone"
            placeholder="Search by name or phone"
            className="flex-1 rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
          />
          <button
            type="submit"
            className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700"
          >
            Search
          </button>
        </form>
        {data === null ? (
          <ErrorMessage message="Could not load owners. Try again later." />
        ) : data.content.length === 0 ? (
          <EmptyState message="No owners found." />
        ) : (
          <>
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-zinc-300 dark:border-zinc-700">
                  <th className="py-2 pr-4">Name</th>
                  <th className="py-2">Phone</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((o) => (
                  <tr key={o.id} className="border-b border-zinc-200 dark:border-zinc-800">
                    <td className="py-2 pr-4">
                      <Link href={`/reception/owners/${o.id}`} className="underline">
                        {o.name}
                      </Link>
                    </td>
                    <td className="py-2">{o.phone}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination
              basePath="/reception/owners"
              params={q ? { q } : undefined}
              page={data.page}
              totalPages={Math.ceil(data.totalElements / data.size)}
            />
          </>
        )}
      </section>
      <section className="flex flex-col gap-4">
        <h2 className="text-xl font-semibold">Register an owner</h2>
        <OwnerForm />
      </section>
    </main>
  );
}
