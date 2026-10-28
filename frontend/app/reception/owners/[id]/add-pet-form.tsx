"use client";

import { useActionState } from "react";
import { addPet, type OwnerFormState } from "@/app/actions/owners";
import { ErrorMessage } from "@/components/ErrorMessage";
import { PetFields } from "@/components/PetForm";
import { useSubmitKeepingValues } from "@/lib/useSubmitKeepingValues";

export function AddPetForm({ ownerId }: { ownerId: number }) {
  const [state, action, pending] = useActionState<OwnerFormState, FormData>(
    addPet.bind(null, ownerId),
    {},
  );
  const submit = useSubmitKeepingValues(action);
  const errors = state.fieldErrors;

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      <h2 className="text-xl font-semibold">Add a pet</h2>
      <ErrorMessage message={state.error} />
      <PetFields
        errors={{ name: errors?.petName, species: errors?.petSpecies, breed: errors?.petBreed }}
      />
      <button
        type="submit"
        disabled={pending}
        className="self-start rounded bg-zinc-900 px-4 py-2 text-white disabled:opacity-60 dark:bg-zinc-100 dark:text-zinc-900"
      >
        {pending ? "Adding..." : "Add pet"}
      </button>
    </form>
  );
}
