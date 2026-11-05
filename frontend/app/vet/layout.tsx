import { Suspense, type ReactNode } from "react";
import { requireRole } from "@/lib/auth";

async function RoleGuard({ children }: { children: ReactNode }) {
  await requireRole("VETERINARIAN");
  return <>{children}</>;
}

export default function VetLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={<p className="px-4 py-8">Loading...</p>}>
      <RoleGuard>{children}</RoleGuard>
    </Suspense>
  );
}
