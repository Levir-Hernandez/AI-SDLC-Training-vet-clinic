"use client";

import { useActionState, useState } from "react";
import {
  createVeterinarian,
  type CreateVeterinarianState,
} from "@/app/actions/veterinarians";
import { ErrorMessage } from "@/components/ErrorMessage";
import { FormField } from "@/components/FormField";
import type { Weekday } from "@/lib/types";

const DAYS: Weekday[] = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

const initialState: CreateVeterinarianState = {};

export function VeterinarianForm() {
  const [state, action, pending] = useActionState(
    createVeterinarian,
    initialState,
  );
  const [rows, setRows] = useState<number[]>([0]);
  const [nextId, setNextId] = useState(1);
  const [clientError, setClientError] = useState<string>();

  function validate(event: React.FormEvent<HTMLFormElement>) {
    const data = new FormData(event.currentTarget);
    const starts = data.getAll("start").map(String);
    const ends = data.getAll("end").map(String);
    const bad = starts.some((s, i) => !s || !ends[i] || s >= ends[i]);
    if (bad) {
      event.preventDefault();
      setClientError("Each schedule block needs a start time before its end time.");
    } else {
      setClientError(undefined);
    }
  }

  const fe = state.fieldErrors ?? {};
  const scheduleError =
    clientError ??
    fe["schedule"] ??
    Object.entries(fe).find(([k]) => k.startsWith("schedule"))?.[1];

  return (
    <form
      action={action}
      onSubmit={validate}
      className="flex flex-col gap-4"
      noValidate
    >
      <FormField name="name" label="Name" error={fe.name} />
      <FormField name="specialization" label="Specialization" error={fe.specialization} />
      <FormField
        name="costPerService"
        label="Cost per service"
        type="number"
        step="0.01"
        min="0"
        error={fe.costPerService}
      />
      <FormField name="username" label="Username" autoComplete="off" error={fe.username} />
      <FormField
        name="password"
        label="Password"
        type="password"
        autoComplete="new-password"
        error={fe.password}
      />
      <fieldset className="flex flex-col gap-2">
        <legend className="text-sm font-medium">Weekly schedule</legend>
        {rows.map((id, index) => (
          <div key={id} className="flex flex-wrap items-center gap-2">
            <select
              name="day"
              aria-label={`Day ${index + 1}`}
              defaultValue="MONDAY"
              className="rounded border border-zinc-300 px-2 py-2 dark:border-zinc-700 dark:bg-zinc-900"
            >
              {DAYS.map((d) => (
                <option key={d} value={d}>
                  {d.charAt(0) + d.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
            <input
              type="time"
              name="start"
              aria-label={`Start ${index + 1}`}
              className="rounded border border-zinc-300 px-2 py-2 dark:border-zinc-700 dark:bg-zinc-900"
            />
            <input
              type="time"
              name="end"
              aria-label={`End ${index + 1}`}
              className="rounded border border-zinc-300 px-2 py-2 dark:border-zinc-700 dark:bg-zinc-900"
            />
            {rows.length > 1 ? (
              <button
                type="button"
                onClick={() => setRows(rows.filter((r) => r !== id))}
                className="text-sm underline"
              >
                Remove
              </button>
            ) : null}
          </div>
        ))}
        <button
          type="button"
          onClick={() => {
            setRows([...rows, nextId]);
            setNextId(nextId + 1);
          }}
          className="self-start text-sm underline"
        >
          Add block
        </button>
        <ErrorMessage message={scheduleError} />
      </fieldset>
      <ErrorMessage message={state.error} />
      {state.success ? (
        <p role="status" className="text-green-700 dark:text-green-400">
          Veterinarian registered.
        </p>
      ) : null}
      <button
        type="submit"
        disabled={pending}
        className="rounded bg-zinc-900 px-4 py-2 text-white disabled:opacity-60 dark:bg-zinc-100 dark:text-zinc-900"
      >
        {pending ? "Saving..." : "Register veterinarian"}
      </button>
    </form>
  );
}
