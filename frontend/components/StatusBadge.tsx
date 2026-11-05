import type { AppointmentStatus } from "@/lib/types";

const STYLES: Record<AppointmentStatus, { label: string; className: string }> = {
  RESERVED: { label: "Reserved", className: "bg-amber-100 text-amber-900" },
  SCHEDULED: { label: "Scheduled", className: "bg-blue-100 text-blue-900" },
  IN_PROGRESS: { label: "In progress", className: "bg-indigo-100 text-indigo-900" },
  FINISHED: { label: "Finished", className: "bg-teal-100 text-teal-900" },
  PAID: { label: "Paid", className: "bg-green-100 text-green-900" },
  CLOSED: { label: "Closed", className: "bg-zinc-200 text-zinc-900" },
  RESCHEDULED: { label: "Rescheduled", className: "bg-purple-100 text-purple-900" },
  CANCELLED: { label: "Cancelled", className: "bg-red-100 text-red-900" },
};

export function StatusBadge({ status, debt = false }: { status: AppointmentStatus; debt?: boolean }) {
  const s =
    status === "CLOSED" && debt
      ? { label: "Debt", className: "bg-orange-100 text-orange-900" }
      : STYLES[status];
  return (
    <span
      role="status"
      className={`inline-block rounded px-2 py-0.5 text-sm font-medium ${s.className}`}
    >
      {s.label}
    </span>
  );
}
