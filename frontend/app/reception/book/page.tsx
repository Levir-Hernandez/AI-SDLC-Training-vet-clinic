import { ErrorMessage } from "@/components/ErrorMessage";
import { apiGet } from "@/lib/api";
import { requireRole } from "@/lib/auth";
import type { PageResponse, Service, Veterinarian } from "@/lib/types";
import { WalkInForm } from "./walk-in-form";

export const instant = false;

export default async function BookWalkInPage() {
  await requireRole("RECEPTIONIST");

  let services: Service[] | null = null;
  let vets: Veterinarian[] | null = null;
  try {
    services = await apiGet<Service[]>("/api/services");
    vets = (await apiGet<PageResponse<Veterinarian>>("/api/veterinarians?size=100")).content;
  } catch {
    services = null;
  }

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-6 px-4 py-8">
      <h1 className="text-2xl font-semibold">Book a walk-in</h1>
      {services === null || vets === null ? (
        <ErrorMessage message="Could not load services and veterinarians. Try again later." />
      ) : (
        <WalkInForm services={services} veterinarians={vets} />
      )}
    </main>
  );
}
