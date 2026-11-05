import { redirect } from "next/navigation";
import { requireRole } from "@/lib/auth";

// Role landing routes; until they exist the welcome below is rendered.
const LANDING_READY: Record<string, string | null> = {
  RECEPTIONIST: null, // "/reception/appointments"
  OWNER: "/owner/appointments",
  VETERINARIAN: "/vet/appointments",
};

export const instant = false;

export default async function Home() {
  const user = await requireRole("RECEPTIONIST", "OWNER", "VETERINARIAN");
  const target = LANDING_READY[user.role];
  if (target) redirect(target);
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-4 px-4 py-16">
      <h1 className="text-3xl font-semibold">Welcome, {user.displayName}</h1>
      <p className="text-zinc-600 dark:text-zinc-400">
        You are signed in as {user.role.toLowerCase()}.
      </p>
    </main>
  );
}
