"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import {
  cancelAppointment,
  closeAppointment,
  confirmAppointment,
  finishAppointment,
  payAppointment,
  startAppointment,
} from "@/app/actions/appointments";
import { CancelDialog } from "@/components/CancelDialog";
import { ErrorMessage } from "@/components/ErrorMessage";
import { PaymentDialog } from "@/components/PaymentDialog";
import type { AppointmentAction, AppointmentStatus } from "@/lib/types";

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
  total,
  status,
}: {
  appointmentId?: number;
  allowedActions: AppointmentAction[];
  handlers?: ActionHandlers;
  total?: number;
  status?: AppointmentStatus;
}) {
  const [payOpen, setPayOpen] = useState(false);
  const [payError, setPayError] = useState<string | undefined>();
  const router = useRouter();
  const [error, setError] = useState<string | undefined>();
  const [pending, startTransition] = useTransition();
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelError, setCancelError] = useState<string | undefined>();
  const [cancelReasonError, setCancelReasonError] = useState<string | undefined>();

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
    for (const [action, run] of [
      ["START", startAppointment],
      ["FINISH", finishAppointment],
    ] as const) {
      all[action] = () => {
        setError(undefined);
        startTransition(async () => {
          const result = await run(appointmentId);
          if (result.ok) router.refresh();
          else setError(result.error);
        });
      };
    }
    if (total !== undefined) {
      all.PAY = () => {
        setError(undefined);
        setPayError(undefined);
        setPayOpen(true);
      };
    }
    all.CLOSE = () => {
      const debt = status === "FINISHED";
      const msg = debt
        ? "Close this appointment unpaid? It will be recorded as a debt."
        : "Close this appointment?";
      if (!window.confirm(msg)) return;
      setError(undefined);
      startTransition(async () => {
        const result = await closeAppointment(appointmentId);
        if (result.ok) router.refresh();
        else setError(result.error);
      });
    };
    all.RESCHEDULE =() => router.push(`/appointments/${appointmentId}/reschedule`);
    all.CANCEL = () => {
      setError(undefined);
      setCancelError(undefined);
      setCancelOpen(true);
    };
  }

  function doCancel(reason: string) {
    if (appointmentId === undefined) return;
    setCancelError(undefined);
    setCancelReasonError(undefined);
    startTransition(async () => {
      const result = await cancelAppointment(appointmentId, reason);
      if (result.ok) {
        setCancelOpen(false);
        router.refresh();
      } else {
        setCancelReasonError(result.reasonError);
        setCancelError(result.error);
      }
    });
  }

  function doPay(amount: string) {
    if (appointmentId === undefined) return;
    setPayError(undefined);
    startTransition(async () => {
      const result = await payAppointment(appointmentId, amount);
      if (result.ok) {
        setPayOpen(false);
        router.refresh();
      } else {
        setPayError(result.error);
      }
    });
  }

  function label(a: AppointmentAction): string {
    if (a === "CLOSE" && status === "FINISHED") return "Close unpaid (debt)";
    return LABELS[a];
  }

  const wired =allowedActions.filter((a) => all[a]);
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
            {label(a)}
          </button>
        ))}
      </div>
      <ErrorMessage message={error} />
      {payOpen && total !== undefined && (
        <PaymentDialog
          total={total}
          pending={pending}
          error={payError}
          onConfirm={doPay}
          onClose={() => setPayOpen(false)}
        />
      )}
      {cancelOpen && (
        <CancelDialog
          pending={pending}
          reasonError={cancelReasonError}
          error={cancelError}
          onConfirm={doCancel}
          onClose={() => setCancelOpen(false)}
        />
      )}
    </div>
  );
}
