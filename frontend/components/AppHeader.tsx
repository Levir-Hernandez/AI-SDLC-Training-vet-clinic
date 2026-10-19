import Link from "next/link";
import { signOut } from "@/app/actions/auth";
import { getCurrentUser } from "@/lib/auth";
import type { Role } from "@/lib/types";

export const ROLE_LABEL: Record<Role, string> = {
  OWNER: "Owner",
  RECEPTIONIST: "Receptionist",
  VETERINARIAN: "Veterinarian",
};

// Role-specific links; later increments add their screens here.
const NAV: Record<Role, { href: string; label: string }[]> = {
  OWNER: [{ href: "/", label: "Home" }],
  RECEPTIONIST: [{ href: "/", label: "Home" }],
  VETERINARIAN: [{ href: "/", label: "Home" }],
};

export async function AppHeader() {
  const user = await getCurrentUser();
  if (!user) return null;

  return (
    <header className="border-b border-zinc-200 dark:border-zinc-800">
      <div className="mx-auto flex w-full max-w-5xl flex-wrap items-center gap-4 px-4 py-3">
        <span className="font-semibold">Vet Clinic</span>
        <nav aria-label="Main" className="flex flex-1 gap-3 text-sm">
          {NAV[user.role].map((link) => (
            <Link key={link.href} href={link.href} className="hover:underline">
              {link.label}
            </Link>
          ))}
        </nav>
        <span className="text-sm text-zinc-600 dark:text-zinc-400">
          {user.displayName} ({ROLE_LABEL[user.role]})
        </span>
        <form action={signOut}>
          <button
            type="submit"
            className="rounded border border-zinc-300 px-3 py-1 text-sm hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-900"
          >
            Sign out
          </button>
        </form>
      </div>
    </header>
  );
}
