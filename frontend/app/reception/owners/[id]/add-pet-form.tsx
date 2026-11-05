"use client";

import { useActionState } from "react";
import { addPet, type OwnerActionState } from "@/app/actions/owners";
import { ErrorMessage } from "@/components/ErrorMessage";
import { PetForm } from "@/components/PetForm";

const initialState: OwnerActionState = {};

export function AddPetForm({ ownerId }: { ownerId: number }) {
  const [state, action, pending] = useActionState(addPet, initialState);
  return (
    <form action={action} className="flex flex-col gap-4" noValidate>
      <input type="hidden" name="ownerId" value={ownerId} />
      <PetForm errors={state.fieldErrors} />
      <ErrorMessage message={state.error} />
      {state.success ? (
        <p role="status" className="text-green-700 dark:text-green-400">
          Pet added.
        </p>
      ) : null}
      <button
        type="submit"
        disabled={pending}
        className="rounded bg-zinc-900 px-4 py-2 text-white disabled:opacity-60 dark:bg-zinc-100 dark:text-zinc-900"
      >
        {pending ? "Saving..." : "Add pet"}
      </button>
    </form>
  );
}
