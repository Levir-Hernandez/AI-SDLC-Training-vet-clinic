export function EmptyState({ message }: { message: string }) {
  return (
    <p className="rounded border border-dashed border-zinc-300 p-6 text-center text-zinc-600 dark:border-zinc-700 dark:text-zinc-400">
      {message}
    </p>
  );
}
