"use client";

import type { AppointmentAction } from "@/lib/types";

const LABELS: Record<AppointmentAction, string> = {
  CONFIRM: "Confirm",
  CANCEL: "Cancel",
  EXPIRE: "Expire",
  START: "Start",
  FINISH: "Finish",
  RESCHEDULE: "Reschedule",
  PAY: "Pay",
  CLOSE: "Close",
  EDIT_OBSERVATIONS: "Edit observations",
};

/** Handlers are wired by later increments; only allowed actions with a handler render a button. */
export type ActionHandlers = Partial<Record<AppointmentAction, () => void>>;

export function AppointmentActions({
  allowedActions,
  handlers = {},
}: {
  allowedActions: AppointmentAction[];
  handlers?: ActionHandlers;
}) {
  const wired = allowedActions.filter((a) => handlers[a]);
  if (wired.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-2">
      {wired.map((a) => (
        <button
          key={a}
          type="button"
          onClick={handlers[a]}
          className="rounded border border-zinc-300 px-3 py-1 text-sm dark:border-zinc-700"
        >
          {LABELS[a]}
        </button>
      ))}
    </div>
  );
}
