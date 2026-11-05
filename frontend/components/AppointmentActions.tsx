"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { confirmAppointment } from "@/app/actions/appointments";
import { ErrorMessage } from "@/components/ErrorMessage";
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
  appointmentId,
  allowedActions,
  handlers = {},
}: {
  appointmentId?: number;
  allowedActions: AppointmentAction[];
  handlers?: ActionHandlers;
}) {
  const router = useRouter();
  const [error, setError] = useState<string | undefined>();
  const [pending, startTransition] = useTransition();

  const all: ActionHandlers = { ...handlers };
  if (appointmentId !== undefined) {
    all.CONFIRM = () => {
      setError(undefined);
      startTransition(async () => {
        const result = await confirmAppointment(appointmentId);
        if (result.ok) router.refresh();
        else setError(result.error);
      });
    };
  }

  const wired = allowedActions.filter((a) => all[a]);
  if (wired.length === 0) return null;
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap gap-2">
        {wired.map((a) => (
          <button
            key={a}
            type="button"
            onClick={all[a]}
            disabled={pending}
            className="rounded border border-zinc-300 px-3 py-1 text-sm disabled:opacity-50 dark:border-zinc-700"
          >
            {LABELS[a]}
          </button>
        ))}
      </div>
      <ErrorMessage message={error} />
    </div>
  );
}
