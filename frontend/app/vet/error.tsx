"use client";

export default function RouteError({ reset }: { error: Error; reset: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-start gap-3 p-6">
      <p className="text-sm text-red-700">Something went wrong while loading this page.</p>
      <button type="button" onClick={() => reset()} className="rounded border border-zinc-300 px-3 py-1 text-sm">
        Try again
      </button>
    </div>
  );
}
