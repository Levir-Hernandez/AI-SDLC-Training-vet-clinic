"use client";

import { useEffect, useRef, useState } from "react";
import { ErrorMessage } from "@/components/ErrorMessage";

export function CancelDialog({
  pending,
  reasonError,
  error,
  onConfirm,
  onClose,
}: {
  pending: boolean;
  reasonError?: string;
  error?: string;
  onConfirm: (reason: string) => void;
  onClose: () => void;
}) {
  const [reason, setReason] = useState("");
  const ref = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === "Escape") {
      onClose();
      return;
    }
    if (e.key !== "Tab" || !ref.current) return;
    const items = ref.current.querySelectorAll<HTMLElement>("textarea, button:not(:disabled)");
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

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby="cancel-title"
        onKeyDown={onKeyDown}
        className="flex w-full max-w-md flex-col gap-3 rounded bg-white p-4 text-zinc-900 dark:bg-zinc-900 dark:text-zinc-100"
      >
        <h2 id="cancel-title" className="text-lg font-semibold">
          Cancel appointment
        </h2>
        <label htmlFor="cancel-reason" className="text-sm">
          Reason
        </label>
        <textarea
          id="cancel-reason"
          ref={inputRef}
          value={reason}
          maxLength={500}
          onChange={(e) => setReason(e.target.value)}
          aria-invalid={reasonError ? true : undefined}
          className="rounded border border-zinc-300 p-2 text-sm dark:border-zinc-700 dark:bg-zinc-800"
          rows={3}
        />
        <ErrorMessage message={reasonError ?? error} />
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded border border-zinc-300 px-3 py-1 text-sm dark:border-zinc-700"
          >
            Keep appointment
          </button>
          <button
            type="button"
            onClick={() => onConfirm(reason.trim())}
            disabled={pending || reason.trim() === ""}
            className="rounded bg-red-700 px-3 py-1 text-sm text-white disabled:opacity-50"
          >
            Confirm cancellation
          </button>
        </div>
      </div>
    </div>
  );
}
