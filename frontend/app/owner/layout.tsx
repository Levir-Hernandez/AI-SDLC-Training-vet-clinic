import { Suspense } from "react";
import { requireRole } from "@/lib/auth";

async function RoleGuard({ children }: { children: React.ReactNode }) {
  await requireRole("OWNER");
  return <>{children}</>;
}

export default function OwnerLayout({ children }: LayoutProps<"/owner">) {
  return (
    <Suspense fallback={<p className="px-4 py-12 text-zinc-500">Loading...</p>}>
      <RoleGuard>{children}</RoleGuard>
    </Suspense>
  );
}
