"use client";

/** Submit button that asks the user to confirm before the surrounding form is submitted. */
export function ConfirmButton({ label, message }: { label: string; message: string }) {
  return (
    <button
      type="submit"
      onClick={(event) => {
        if (!window.confirm(message)) event.preventDefault();
      }}
      className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700"
    >
      {label}
    </button>
  );
}
