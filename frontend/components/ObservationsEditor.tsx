"use client";

import { useActionState } from "react";
import { saveObservations, type ObservationsState } from "@/app/actions/appointments";
import { ErrorMessage } from "@/components/ErrorMessage";

export function ObservationsEditor({
  appointmentId,
  initial,
}: {
  appointmentId: number;
  initial: string | null;
}) {
  const [state, action, pending] = useActionState<ObservationsState, FormData>(
    saveObservations,
    {},
  );
  return (
    <form action={action} className="flex flex-col gap-2">
      <input type="hidden" name="id" value={appointmentId} />
      <label htmlFor="observations" className="text-sm font-medium">
        Observations
      </label>
      <textarea
        id="observations"
        name="observations"
        defaultValue={initial ?? ""}
        maxLength={2000}
        rows={5}
        className="rounded border border-zinc-300 p-2 text-sm dark:border-zinc-700"
      />
      <div className="flex items-center gap-3">
        <button
          type="submit"
          disabled={pending}
          className="rounded border border-zinc-300 px-3 py-1 text-sm disabled:opacity-50 dark:border-zinc-700"
        >
          Save
        </button>
        {state.saved && !pending && <span className="text-sm">Saved.</span>}
      </div>
      <ErrorMessage message={state.error} />
    </form>
  );
}
