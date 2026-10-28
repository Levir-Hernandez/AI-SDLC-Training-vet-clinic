"use client";

import { startTransition, type FormEvent } from "react";

/**
 * Submit handler that dispatches a `useActionState` action without React's automatic
 * form reset, so a rejected submission keeps what the user typed and the selects keep
 * showing the values that are actually submitted. Successful actions redirect away.
 */
export function useSubmitKeepingValues(dispatch: (formData: FormData) => void) {
  return (event: FormEvent<HTMLFormElement>) => {
    if (event.defaultPrevented) return;
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    startTransition(() => dispatch(formData));
  };
}
