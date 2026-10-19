import Link from "next/link";

type Props = {
  basePath: string;
  page: number;
  size: number;
  totalElements: number;
};

const ENABLED =
  "rounded border border-zinc-300 px-3 py-1 hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-900";
const DISABLED = "rounded border border-zinc-200 px-3 py-1 text-zinc-400 dark:border-zinc-800";

/** Previous/next links driven by the `page` query parameter (zero-based, like the API). */
export function Pagination({ basePath, page, size, totalElements }: Props) {
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  if (totalPages <= 1) return null;

  return (
    <nav aria-label="Pagination" className="flex items-center gap-3 text-sm">
      {page > 0 ? (
        <Link href={`${basePath}?page=${page - 1}`} className={ENABLED}>
          Previous
        </Link>
      ) : (
        <span aria-disabled="true" className={DISABLED}>
          Previous
        </span>
      )}
      <span>
        Page {page + 1} of {totalPages}
      </span>
      {page + 1 < totalPages ? (
        <Link href={`${basePath}?page=${page + 1}`} className={ENABLED}>
          Next
        </Link>
      ) : (
        <span aria-disabled="true" className={DISABLED}>
          Next
        </span>
      )}
    </nav>
  );
}
