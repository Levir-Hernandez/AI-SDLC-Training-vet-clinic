"use client";

import { useEffect, useRef, useState, useTransition } from "react";
import { cancelAppointment } from "@/app/actions/appointments";
import { ErrorMessage } from "@/components/ErrorMessage";

/** Cancel button that opens a modal asking for the required reason. */
export function CancelDialog({ appointmentId }: { appointmentId: number }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string>();
  const [reasonError, setReasonError] = useState<string>();
  const [pending, startTransition] = useTransition();
  const dialogRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (open) textareaRef.current?.focus();
  }, [open]);

  function close() {
    setOpen(false);
    setError(undefined);
    setReasonError(undefined);
  }

  // Keep Tab inside the dialog and let Escape close it.
  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === "Escape" && !pending) {
      close();
      return;
    }
    if (event.key !== "Tab") return;
    const focusable = dialogRef.current?.querySelectorAll<HTMLElement>("textarea, button:not([disabled])");
    if (!focusable || focusable.length === 0) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  function confirm() {
    setError(undefined);
    setReasonError(undefined);
    startTransition(async () => {
      const result = await cancelAppointment(appointmentId, reason.trim());
      if (result?.reasonError) setReasonError(result.reasonError);
      else if (result?.error) setError(result.error);
    });
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700"
      >
        Cancel
      </button>
      {open ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby="cancel-title"
            onKeyDown={onKeyDown}
            className="flex w-full max-w-md flex-col gap-3 rounded bg-white p-6 dark:bg-zinc-900"
          >
            <h2 id="cancel-title" className="text-lg font-semibold">
              Cancel appointment
            </h2>
            <label htmlFor="cancel-reason" className="text-sm">
              Reason (required)
            </label>
            <textarea
              id="cancel-reason"
              ref={textareaRef}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={500}
              rows={3}
              aria-invalid={reasonError ? true : undefined}
              className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-950"
            />
            {reasonError ? <p className="text-sm text-red-700 dark:text-red-300">{reasonError}</p> : null}
            <ErrorMessage message={error} />
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={close}
                disabled={pending}
                className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700"
              >
                Keep appointment
              </button>
              <button
                type="button"
                onClick={confirm}
                disabled={pending || reason.trim() === ""}
                className="rounded bg-red-700 px-3 py-2 text-sm text-white disabled:opacity-50"
              >
                {pending ? "Cancelling..." : "Confirm cancellation"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
