"use client";

import { ErrorMessage } from "@/components/ErrorMessage";

export default function RouteError({ unstable_retry }: { error: Error & { digest?: string }; unstable_retry: () => void }) {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col items-start gap-4 px-4 py-8">
      <ErrorMessage message="Something went wrong while loading this page." />
      <button
        type="button"
        onClick={() => unstable_retry()}
        className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700"
      >
        Try again
      </button>
    </main>
  );
}
