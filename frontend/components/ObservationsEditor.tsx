"use client";

import { useActionState } from "react";
import { saveObservations, type ObservationsState } from "@/app/actions/appointments";

/** Textarea + Save for the visit observations; the parent renders it only when EDIT_OBSERVATIONS is allowed. */
export function ObservationsEditor({ appointmentId, initial }: { appointmentId: number; initial: string }) {
  const [state, action, pending] = useActionState<ObservationsState, FormData>(
    saveObservations.bind(null, appointmentId),
    {},
  );
  return (
    <form action={action} className="flex flex-col gap-2">
      <label htmlFor="observations" className="text-sm font-medium">
        Observations
      </label>
      <textarea
        id="observations"
        name="observations"
        defaultValue={initial}
        maxLength={2000}
        rows={5}
        className="rounded border border-zinc-300 bg-transparent p-2 text-sm dark:border-zinc-700"
      />
      <div className="flex items-center gap-3">
        <button
          type="submit"
          disabled={pending}
          className="rounded border border-zinc-300 px-3 py-2 text-sm disabled:opacity-50 dark:border-zinc-700"
        >
          {pending ? "Saving..." : "Save observations"}
        </button>
        {state.saved ? (
          <span role="status" className="text-sm text-green-700 dark:text-green-400">
            Observations saved.
          </span>
        ) : null}
        {state.error ? (
          <span role="alert" className="text-sm text-red-700 dark:text-red-400">
            {state.error}
          </span>
        ) : null}
      </div>
    </form>
  );
}
