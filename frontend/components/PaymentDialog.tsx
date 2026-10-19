"use client";

import { useEffect, useRef, useState, useTransition } from "react";
import { payAppointment } from "@/app/actions/appointments";
import { ErrorMessage } from "@/components/ErrorMessage";

/** Record-payment button that opens a modal with the total and an editable amount (prefilled with the total). */
export function PaymentDialog({ appointmentId, total }: { appointmentId: number; total: number }) {
  const [open, setOpen] = useState(false);
  const [amount, setAmount] = useState(total.toFixed(2));
  const [error, setError] = useState<string>();
  const [amountError, setAmountError] = useState<string>();
  const [pending, startTransition] = useTransition();
  const dialogRef = useRef<HTMLFormElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  function close() {
    setOpen(false);
    setError(undefined);
    setAmountError(undefined);
    setAmount(total.toFixed(2));
  }

  // Keep Tab inside the dialog and let Escape close it.
  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === "Escape" && !pending) {
      close();
      return;
    }
    if (event.key !== "Tab") return;
    const focusable = dialogRef.current?.querySelectorAll<HTMLElement>("input, button:not([disabled])");
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

  function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(undefined);
    setAmountError(undefined);
    startTransition(async () => {
      const result = await payAppointment(appointmentId, amount.trim());
      if (result?.amountError) setAmountError(result.amountError);
      else if (result?.error) setError(result.error);
    });
  }

  const title = `payment-title-${appointmentId}`;
  const inputId = `payment-amount-${appointmentId}`;

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700"
      >
        Record payment
      </button>
      {open ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <form
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={title}
            onKeyDown={onKeyDown}
            onSubmit={submit}
            className="flex w-full max-w-md flex-col gap-3 rounded bg-white p-6 dark:bg-zinc-900"
          >
            <h2 id={title} className="text-lg font-semibold">
              Record payment
            </h2>
            <p className="text-sm">Total due: {total.toFixed(2)}</p>
            <label htmlFor={inputId} className="text-sm">
              Amount (must equal the total)
            </label>
            <input
              id={inputId}
              ref={inputRef}
              type="number"
              step="0.01"
              min="0"
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              aria-invalid={amountError ? true : undefined}
              className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-zinc-950"
            />
            {amountError ? <p className="text-sm text-red-700 dark:text-red-300">{amountError}</p> : null}
            <ErrorMessage message={error} />
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={close}
                disabled={pending}
                className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700"
              >
                Back
              </button>
              <button
                type="submit"
                disabled={pending || amount.trim() === ""}
                className="rounded bg-teal-700 px-3 py-2 text-sm text-white disabled:opacity-50"
              >
                {pending ? "Saving..." : "Confirm payment"}
              </button>
            </div>
          </form>
        </div>
      ) : null}
    </>
  );
}
