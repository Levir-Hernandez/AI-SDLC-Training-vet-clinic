"use client";

import { useActionState } from "react";
import { createOwner, type OwnerActionState } from "@/app/actions/owners";
import { ErrorMessage } from "@/components/ErrorMessage";
import { FormField } from "@/components/FormField";
import { PetForm } from "@/components/PetForm";

const initialState: OwnerActionState = {};

export function OwnerForm() {
  const [state, action, pending] = useActionState(createOwner, initialState);
  const fe = state.fieldErrors ?? {};

  return (
    <form action={action} className="flex flex-col gap-4" noValidate>
      <FormField name="name" label="Name" error={fe.name} />
      <FormField name="phone" label="Phone (used to sign in)" type="tel" error={fe.phone} />
      <FormField
        name="password"
        label="Password"
        type="password"
        autoComplete="new-password"
        error={fe.password}
      />
      <fieldset className="flex flex-col gap-4">
        <legend className="text-sm font-medium">First pet (optional)</legend>
        <PetForm errors={fe} required={false} />
      </fieldset>
      <ErrorMessage message={state.error} />
      {state.success ? (
        <p role="status" className="text-green-700 dark:text-green-400">
          Owner registered.
        </p>
      ) : null}
      <button
        type="submit"
        disabled={pending}
        className="rounded bg-zinc-900 px-4 py-2 text-white disabled:opacity-60 dark:bg-zinc-100 dark:text-zinc-900"
      >
        {pending ? "Saving..." : "Register owner"}
      </button>
    </form>
  );
}
