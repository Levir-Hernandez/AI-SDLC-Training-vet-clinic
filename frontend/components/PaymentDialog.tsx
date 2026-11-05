"use client";

import { useEffect, useRef, useState } from "react";
import { ErrorMessage } from "@/components/ErrorMessage";

export function PaymentDialog({
  total,
  pending,
  error,
  onConfirm,
  onClose,
}: {
  total: number;
  pending: boolean;
  error?: string;
  onConfirm: (amount: string) => void;
  onClose: () => void;
}) {
  const [amount, setAmount] = useState(total.toFixed(2));
  const ref = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === "Escape") {
      onClose();
      return;
    }
    if (e.key !== "Tab" || !ref.current) return;
    const items = ref.current.querySelectorAll<HTMLElement>("input, button:not(:disabled)");
    if (items.length === 0) return;
    const first = items[0];
    const last = items[items.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  }

  const valid = /^\d+(\.\d{1,2})?$/.test(amount.trim());

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby="payment-title"
        onKeyDown={onKeyDown}
        className="flex w-full max-w-md flex-col gap-3 rounded bg-white p-4 text-zinc-900 dark:bg-zinc-900 dark:text-zinc-100"
      >
        <h2 id="payment-title" className="text-lg font-semibold">
          Record payment
        </h2>
        <p className="text-sm">Total due: ${total.toFixed(2)}</p>
        <label htmlFor="payment-amount" className="text-sm">
          Amount
        </label>
        <input
          id="payment-amount"
          ref={inputRef}
          inputMode="decimal"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          className="rounded border border-zinc-300 p-2 text-sm dark:border-zinc-700 dark:bg-zinc-800"
        />
        <ErrorMessage message={error} />
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded border border-zinc-300 px-3 py-1 text-sm dark:border-zinc-700"
          >
            Back
          </button>
          <button
            type="button"
            onClick={() => onConfirm(amount.trim())}
            disabled={pending || !valid}
            className="rounded bg-green-700 px-3 py-1 text-sm text-white disabled:opacity-50"
          >
            Confirm payment
          </button>
        </div>
      </div>
    </div>
  );
}
