"use client";

import { useActionState } from "react";
import { signIn, type SignInState } from "@/app/actions/auth";
import { ErrorMessage } from "@/components/ErrorMessage";
import { FormField } from "@/components/FormField";

export function LoginForm() {
  const [state, action, pending] = useActionState<SignInState, FormData>(signIn, {});

  return (
    <form action={action} className="flex flex-col gap-4" noValidate>
      <ErrorMessage message={state.error} />
      <FormField
        label="Username"
        name="username"
        autoComplete="username"
        required
        error={state.fieldErrors?.username}
      />
      <FormField
        label="Password"
        name="password"
        type="password"
        autoComplete="current-password"
        required
        error={state.fieldErrors?.password}
      />
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
