import { redirect } from "next/navigation";
import { Suspense } from "react";
import { ROLE_LABEL } from "@/components/AppHeader";
import { requireRole } from "@/lib/auth";

// Role landing screens are added by later increments; until then show a welcome.
async function Welcome() {
  const user = await requireRole("OWNER", "RECEPTIONIST", "VETERINARIAN");
  if (user.role === "OWNER") redirect("/owner/appointments");
  if (user.role === "VETERINARIAN") redirect("/vet/appointments");
  if (user.role === "RECEPTIONIST") redirect("/reception/appointments");
  return (
    <>
      <h1 className="text-3xl font-semibold">Welcome, {user.displayName}</h1>
      <p className="text-zinc-600 dark:text-zinc-400">
        Signed in as {ROLE_LABEL[user.role]}.
      </p>
    </>
  );
}

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-4 px-4 py-12">
      <Suspense fallback={<p className="text-zinc-500">Loading...</p>}>
        <Welcome />
      </Suspense>
    </main>
  );
}
