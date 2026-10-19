"use client";

import { useActionState } from "react";
import { createOwner, type OwnerFormState } from "@/app/actions/owners";
import { ErrorMessage } from "@/components/ErrorMessage";
import { FormField } from "@/components/FormField";
import { PetFields } from "@/components/PetForm";

export function OwnerForm() {
  const [state, action, pending] = useActionState<OwnerFormState, FormData>(createOwner, {});
  const errors = state.fieldErrors;

  return (
    <form action={action} noValidate className="flex flex-col gap-4">
      <h2 className="text-xl font-semibold">Register an owner</h2>
      <ErrorMessage message={state.error} />
      <div className="grid gap-4 sm:grid-cols-3">
        <FormField label="Name" name="name" required error={errors?.name} />
        <FormField label="Phone" name="phone" type="tel" required error={errors?.phone} />
        <FormField
          label="Password (min. 6 characters)"
          name="password"
          type="password"
          autoComplete="new-password"
          required
          error={errors?.password}
        />
      </div>
      <fieldset className="flex flex-col gap-2">
        <legend className="text-sm font-medium">First pet (optional)</legend>
        <PetFields
          errors={{ name: errors?.petName, species: errors?.petSpecies, breed: errors?.petBreed }}
        />
      </fieldset>
      <button
        type="submit"
        disabled={pending}
        className="self-start rounded bg-zinc-900 px-4 py-2 text-white disabled:opacity-60 dark:bg-zinc-100 dark:text-zinc-900"
      >
        {pending ? "Registering..." : "Register owner"}
      </button>
    </form>
  );
}
