import Link from "next/link";
import { signOut } from "@/app/actions/auth";
import type { CurrentUser, Role } from "@/lib/types";

const NAV: Record<Role, { href: string; label: string }[]> = {
  RECEPTIONIST: [
    { href: "/reception/appointments", label: "Appointments" },
    { href: "/reception/book", label: "Book walk-in" },
    { href: "/reception/debts", label: "Debts" },
    { href: "/reception/owners", label: "Owners" },
    { href: "/reception/veterinarians", label: "Veterinarians" },
  ],
  VETERINARIAN: [{ href: "/vet/appointments", label: "My appointments" }],
  OWNER: [
    { href: "/owner/appointments", label: "My appointments" },
    { href: "/owner/book", label: "Book" },
    { href: "/owner/pets", label: "My pets" },
  ],
};

const ROLE_LABEL: Record<Role, string> = {
  RECEPTIONIST: "Receptionist",
  VETERINARIAN: "Veterinarian",
  OWNER: "Owner",
};

export function AppHeader({ user }: { user: CurrentUser }) {
  return (
    <header className="flex flex-wrap items-center gap-4 border-b border-zinc-200 px-4 py-3 dark:border-zinc-800">
      <Link href="/" className="font-semibold">
        Vet Clinic
      </Link>
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
          className="rounded border border-zinc-300 px-3 py-1 text-sm dark:border-zinc-700"
        >
          Sign out
        </button>
      </form>
    </header>
  );
}
