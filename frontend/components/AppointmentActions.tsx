import { CancelDialog } from "@/components/CancelDialog";
import type { AppointmentAction } from "@/lib/types";

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
}: {
  actions: AppointmentAction[];
  /** Needed to offer CANCEL, which opens a dialog instead of using a form handler. */
  appointmentId?: number;
  handlers?: Partial<Record<AppointmentAction, Handler>>;
}) {
  const available = actions.filter((a) => (a === "CANCEL" ? appointmentId !== undefined : handlers[a]));
  if (available.length === 0) {
    return <p className="text-sm text-zinc-500">No actions available for this appointment.</p>;
  }
  return (
    <div className="flex flex-wrap gap-2" role="group" aria-label="Appointment actions">
      {available.map((action) =>
        action === "CANCEL" ? (
          <CancelDialog key={action} appointmentId={appointmentId!} />
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
