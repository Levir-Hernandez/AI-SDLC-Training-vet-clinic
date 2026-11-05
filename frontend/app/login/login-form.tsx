"use client";

import { useActionState } from "react";
import { signIn, type SignInState } from "@/app/actions/auth";
import { ErrorMessage } from "@/components/ErrorMessage";
import { FormField } from "@/components/FormField";

const initialState: SignInState = {};

export function LoginForm() {
  const [state, action, pending] = useActionState(signIn, initialState);
  return (
    <form action={action} className="flex flex-col gap-4" noValidate>
      <FormField
        name="username"
        label="Username"
        autoComplete="username"
        error={state.fieldErrors?.username}
      />
      <FormField
        name="password"
        label="Password"
        type="password"
        autoComplete="current-password"
        error={state.fieldErrors?.password}
      />
      <ErrorMessage message={state.error} />
      <button
        type="submit"
        disabled={pending}
        className="rounded bg-zinc-900 px-4 py-2 text-white disabled:opacity-60 dark:bg-zinc-100 dark:text-zinc-900"
      >
        {pending ? "Signing in..." : "Sign in"}
      </button>
    </form>
  );
}
