import Link from "next/link";
import { CancelDialog } from "@/components/CancelDialog";
import { ConfirmButton } from "@/components/ConfirmButton";
import { PaymentDialog } from "@/components/PaymentDialog";
import type { AppointmentAction, AppointmentStatus } from "@/lib/types";

const LABEL: Record<AppointmentAction, string> = {
  CONFIRM: "Confirm",
  CANCEL: "Cancel",
  EXPIRE: "Expire",
  START: "Start visit",
  FINISH: "Finish visit",
  RESCHEDULE: "Reschedule",
  PAY: "Record payment",
  CLOSE: "Close",
  EDIT_OBSERVATIONS: "Edit observations",
};

type Handler = (formData: FormData) => void | Promise<void>;

/**
 * Offers only the actions the backend allows for this user and that already have a wired handler;
 * later increments wire their handlers through the `handlers` prop.
 */
export function AppointmentActions({
  actions,
  appointmentId,
  handlers = {},
  total,
  status,
}: {
  actions: AppointmentAction[];
  /** Needed to offer CANCEL, which opens a dialog instead of using a form handler. */
  appointmentId?: number;
  handlers?: Partial<Record<AppointmentAction, Handler>>;
  /** Needed to offer PAY, which opens a dialog prefilled with the total. */
  total?: number;
  /** Used to label CLOSE ("Close unpaid (debt)" on Finished). */
  status?: AppointmentStatus;
}) {
  const available = actions.filter((a) =>
    a === "CANCEL" || a === "RESCHEDULE"
      ? appointmentId !== undefined
      : a === "PAY"
        ? appointmentId !== undefined && total !== undefined
        : handlers[a],
  );
  if (available.length === 0) {
    return <p className="text-sm text-zinc-500">No actions available for this appointment.</p>;
  }
  return (
    <div className="flex flex-wrap gap-2" role="group" aria-label="Appointment actions">
      {available.map((action) =>
        action === "CANCEL" ? (
          <CancelDialog key={action} appointmentId={appointmentId!} />
        ) : action === "PAY" ? (
          <PaymentDialog key={action} appointmentId={appointmentId!} total={total!} />
        ) : action === "CLOSE" ? (
          <form key={action} action={handlers.CLOSE}>
            <ConfirmButton
              label={status === "FINISHED" ? "Close unpaid (debt)" : "Close"}
              message={
                status === "FINISHED"
                  ? "Close this appointment without payment? It will be recorded as a debt."
                  : "Close this appointment? This is final."
              }
            />
          </form>
        ) : action === "RESCHEDULE" ? (
          <Link
            key={action}
            href={`/appointments/${appointmentId}/reschedule`}
            className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700"
          >
            {LABEL[action]}
          </Link>
        ) : (
        <form key={action} action={handlers[action]}>
          <button type="submit" className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700">
            {LABEL[action]}
          </button>
        </form>
        ),
      )}
    </div>
  );
}
