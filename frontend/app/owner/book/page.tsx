import { ErrorMessage } from "@/components/ErrorMessage";
import { apiGet } from "@/lib/api";
import { requireRole } from "@/lib/auth";
import type { PageResponse, Pet, Service, Veterinarian } from "@/lib/types";
import { OnlineBookingForm } from "./online-booking-form";

export const instant = false;

export default async function OwnerBookPage() {
  await requireRole("OWNER");

  let pets: Pet[] | null = null;
  let services: Service[] | null = null;
  let vets: Veterinarian[] | null = null;
  try {
    pets = (await apiGet<PageResponse<Pet>>("/api/pets?size=100")).content;
    services = await apiGet<Service[]>("/api/services");
    vets = (await apiGet<PageResponse<Veterinarian>>("/api/veterinarians?size=100")).content;
  } catch {
    pets = null;
  }

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-6 px-4 py-8">
      <h1 className="text-2xl font-semibold">Book an appointment</h1>
      {pets === null || services === null || vets === null ? (
        <ErrorMessage message="Could not load the booking data. Try again later." />
      ) : pets.length === 0 ? (
        <p className="text-sm">You have no pets registered yet. Ask reception to add one.</p>
      ) : (
        <OnlineBookingForm pets={pets} services={services} veterinarians={vets} />
      )}
    </main>
  );
}
