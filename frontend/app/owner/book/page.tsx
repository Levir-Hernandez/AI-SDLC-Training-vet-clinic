import { Suspense } from "react";
import { EmptyState } from "@/components/EmptyState";
import { ErrorMessage } from "@/components/ErrorMessage";
import { ApiError, apiGet } from "@/lib/api";
import type { PageResponse, Pet, Service, Veterinarian } from "@/lib/types";
import { OnlineBookingForm } from "./online-booking-form";

export const metadata = { title: "Book online - Vet Clinic" };

async function BookContent() {
  let pets: PageResponse<Pet>;
  let services: Service[];
  let vets: PageResponse<Veterinarian>;
  try {
    [pets, services, vets] = await Promise.all([
      apiGet<PageResponse<Pet>>("/api/pets?size=100"),
      apiGet<Service[]>("/api/services"),
      apiGet<PageResponse<Veterinarian>>("/api/veterinarians?size=100"),
    ]);
  } catch (error) {
    if (error instanceof ApiError) return <ErrorMessage message={error.message} />;
    throw error;
  }
  if (pets.content.length === 0) {
    return <EmptyState message="You have no pets registered yet. Ask reception to add one." />;
  }
  return <OnlineBookingForm pets={pets.content} services={services} veterinarians={vets.content} />;
}

export default function OwnerBookPage() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-6 px-4 py-8">
      <h1 className="text-2xl font-semibold">Book an appointment</h1>
      <Suspense fallback={<p className="text-zinc-500">Loading...</p>}>
        <BookContent />
      </Suspense>
    </main>
  );
}
