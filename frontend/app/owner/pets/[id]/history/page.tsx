import { notFound } from "next/navigation";
import { ErrorMessage } from "@/components/ErrorMessage";
import { PetHistoryView } from "@/components/PetHistoryView";
import { ApiError, apiGet } from "@/lib/api";
import { requireRole } from "@/lib/auth";
import type { Appointment, PageResponse } from "@/lib/types";

export const instant = false;

export default async function OwnerPetHistoryPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  await requireRole("OWNER");
  const { id } = await params;

  let data: PageResponse<Appointment> | null = null;
  try {
    data = await apiGet<PageResponse<Appointment>>(
      `/api/pets/${encodeURIComponent(id)}/history?size=100`,
    );
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    data = null;
  }
  if (data === null) {
    return (
      <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8">
        <ErrorMessage message="Could not load the history. Try again later." />
      </main>
    );
  }
  return <PetHistoryView items={data.content} showDebt={false} />;
}
