"use client";

import { useActionState, useState } from "react";
import { createVeterinarian, type CreateVeterinarianState } from "@/app/actions/veterinarians";
import { ErrorMessage } from "@/components/ErrorMessage";
import { FormField } from "@/components/FormField";
import type { Weekday } from "@/lib/types";
import { useSubmitKeepingValues } from "@/lib/useSubmitKeepingValues";

const WEEKDAYS: { value: Weekday; label: string }[] = [
  { value: "MONDAY", label: "Monday" },
  { value: "TUESDAY", label: "Tuesday" },
  { value: "WEDNESDAY", label: "Wednesday" },
  { value: "THURSDAY", label: "Thursday" },
  { value: "FRIDAY", label: "Friday" },
  { value: "SATURDAY", label: "Saturday" },
  { value: "SUNDAY", label: "Sunday" },
];

type Row = { key: number; day: Weekday; start: string; end: string };

const CONTROL = "rounded border border-zinc-300 bg-transparent px-2 py-1 dark:border-zinc-700";

export function VeterinarianForm() {
  const [state, action, pending] = useActionState<CreateVeterinarianState, FormData>(
    createVeterinarian,
    {},
  );
  const [rows, setRows] = useState<Row[]>([
    { key: 0, day: "MONDAY", start: "09:00", end: "13:00" },
  ]);
  const submit = useSubmitKeepingValues(action);
  const [nextKey, setNextKey] = useState(1);
  const [clientError, setClientError] = useState<string>();

  function update(key: number, patch: Partial<Row>) {
    setRows((current) => current.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  }

  function addRow() {
    setRows((current) => [
      ...current,
      { key: nextKey, day: "MONDAY", start: "09:00", end: "13:00" },
    ]);
    setNextKey(nextKey + 1);
  }

  return (
    <form
      noValidate
      onSubmit={(event) => {
        if (rows.length === 0) {
          event.preventDefault();
          setClientError("Add at least one schedule block.");
        } else if (rows.some((r) => !r.start || !r.end || r.start >= r.end)) {
          event.preventDefault();
          setClientError("Each schedule block must have a start time before its end time.");
        } else {
          setClientError(undefined);
        }
        submit(event);
      }}
      className="flex flex-col gap-4"
    >
      <h2 className="text-xl font-semibold">Register a veterinarian</h2>
      <ErrorMessage message={state.error} />
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label="Name" name="name" required error={state.fieldErrors?.name} />
        <FormField
          label="Specialization"
          name="specialization"
          required
          error={state.fieldErrors?.specialization}
        />
        <FormField
          label="Cost per service"
          name="costPerService"
          type="number"
          min="0"
          step="0.01"
          required
          error={state.fieldErrors?.costPerService}
        />
        <FormField
          label="Username"
          name="username"
          autoComplete="off"
          required
          error={state.fieldErrors?.username}
        />
        <FormField
          label="Password (min. 6 characters)"
          name="password"
          type="password"
          autoComplete="new-password"
          required
          error={state.fieldErrors?.password}
        />
      </div>

      <fieldset className="flex flex-col gap-2">
        <legend className="text-sm font-medium">Weekly schedule</legend>
        {rows.map((row, i) => (
          <div key={row.key} className="flex flex-wrap items-end gap-2">
            <label className="flex flex-col text-sm">
              Day
              <select
                name="day"
                value={row.day}
                onChange={(e) => update(row.key, { day: e.target.value as Weekday })}
                className={CONTROL}
              >
                {WEEKDAYS.map((d) => (
                  <option key={d.value} value={d.value}>
                    {d.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col text-sm">
              Start
              <input
                type="time"
                name="start"
                value={row.start}
                onChange={(e) => update(row.key, { start: e.target.value })}
                className={CONTROL}
              />
            </label>
            <label className="flex flex-col text-sm">
              End
              <input
                type="time"
                name="end"
                value={row.end}
                onChange={(e) => update(row.key, { end: e.target.value })}
                className={CONTROL}
              />
            </label>
            <button
              type="button"
              onClick={() => setRows((current) => current.filter((r) => r.key !== row.key))}
              aria-label={`Remove schedule block ${i + 1}`}
              className="rounded border border-zinc-300 px-2 py-1 text-sm dark:border-zinc-700"
            >
              Remove
            </button>
          </div>
        ))}
        <div>
          <button
            type="button"
            onClick={addRow}
            className="rounded border border-zinc-300 px-3 py-1 text-sm dark:border-zinc-700"
          >
            Add block
          </button>
        </div>
        {clientError || state.scheduleError ? (
          <p role="alert" className="text-sm text-red-600">
            {clientError ?? state.scheduleError}
          </p>
        ) : null}
      </fieldset>

      <button
        type="submit"
        disabled={pending}
        className="self-start rounded bg-zinc-900 px-4 py-2 text-white disabled:opacity-60 dark:bg-zinc-100 dark:text-zinc-900"
      >
        {pending ? "Registering..." : "Register veterinarian"}
      </button>
    </form>
  );
}
