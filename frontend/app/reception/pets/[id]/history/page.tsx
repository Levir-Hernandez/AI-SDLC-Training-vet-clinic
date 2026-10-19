import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { ErrorMessage } from "@/components/ErrorMessage";
import { PetHistory } from "@/components/PetHistory";
import { ApiError, apiGet } from "@/lib/api";
import type { Appointment, PageResponse } from "@/lib/types";

export const metadata = { title: "Pet history - Vet Clinic" };

async function History({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!/^\d+$/.test(id)) notFound();

  let data: PageResponse<Appointment>;
  try {
    data = await apiGet<PageResponse<Appointment>>(`/api/pets/${id}/history?size=100`);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 404) notFound();
      return <ErrorMessage message={error.message} />;
    }
    throw error;
  }

  return <PetHistory appointments={data.content} showDebt />;
}

export default function ReceptionPetHistoryPage({ params }: PageProps<"/reception/pets/[id]/history">) {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-6 px-4 py-8">
      <Link href="/reception/appointments" className="text-sm underline">
        Back to appointments
      </Link>
      <h1 className="text-2xl font-semibold">Pet service history</h1>
      <Suspense fallback={<p className="text-zinc-500">Loading history...</p>}>
        <History params={params} />
      </Suspense>
    </main>
  );
}
