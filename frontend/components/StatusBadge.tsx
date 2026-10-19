import type { AppointmentStatus } from "@/lib/types";

const STATUS: Record<AppointmentStatus, { label: string; classes: string }> = {
  RESERVED: {
    label: "Reserved",
    classes: "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300",
  },
  SCHEDULED: {
    label: "Scheduled",
    classes: "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300",
  },
  IN_PROGRESS: {
    label: "In progress",
    classes: "bg-purple-100 text-purple-800 dark:bg-purple-950 dark:text-purple-300",
  },
  FINISHED: {
    label: "Finished",
    classes: "bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300",
  },
  PAID: {
    label: "Paid",
    classes: "bg-teal-100 text-teal-800 dark:bg-teal-950 dark:text-teal-300",
  },
  CLOSED: {
    label: "Closed",
    classes: "bg-zinc-200 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-200",
  },
  RESCHEDULED: {
    label: "Rescheduled",
    classes: "bg-orange-100 text-orange-800 dark:bg-orange-950 dark:text-orange-300",
  },
  CANCELLED: {
    label: "Cancelled",
    classes: "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300",
  },
};

/** One colour and label per appointment status. */
export function StatusBadge({ status, debt = false }: { status: AppointmentStatus; debt?: boolean }) {
  const { label, classes } =
    status === "CLOSED" && debt
      ? { label: "Debt", classes: "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300" }
      : STATUS[status];
  return (
    <span role="status" className={`inline-block rounded-full px-3 py-1 text-xs font-medium ${classes}`}>
      {label}
    </span>
  );
}
