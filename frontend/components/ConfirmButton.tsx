"use client";

import { useFormStatus } from "react-dom";

/**
 * Submit button for an action form: disabled while the submission is pending and, when a message
 * is given, asks the user to confirm before the form is submitted.
 */
export function ConfirmButton({ label, message }: { label: string; message?: string }) {
  const { pending } = useFormStatus();
  return (
    <button
      type="submit"
      disabled={pending}
      onClick={(event) => {
        if (message && !window.confirm(message)) event.preventDefault();
      }}
      className="rounded border border-zinc-300 px-3 py-2 text-sm disabled:cursor-not-allowed disabled:opacity-60 dark:border-zinc-700"
    >
      {pending ? "Working..." : label}
    </button>
  );
}
