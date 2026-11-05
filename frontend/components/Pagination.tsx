import Link from "next/link";

interface PaginationProps {
  basePath: string;
  page: number;
  totalPages: number;
  /** Extra query params (e.g. a search term) kept on every page link. */
  params?: Record<string, string>;
}

export function Pagination({ basePath, page, totalPages, params }: PaginationProps) {
  if (totalPages <= 1) return null;
  const linkClass = "rounded border border-zinc-300 px-3 py-1 text-sm dark:border-zinc-700";
  const hrefFor = (target: number) => {
    const query = new URLSearchParams({ ...params, page: String(target) });
    return `${basePath}?${query.toString()}`;
  };
  return (
    <nav aria-label="Pagination" className="flex items-center gap-3">
      {page > 0 ? (
        <Link className={linkClass} href={hrefFor(page - 1)}>
          Previous
        </Link>
      ) : null}
      <span className="text-sm">
        Page {page + 1} of {totalPages}
      </span>
      {page + 1 < totalPages ? (
        <Link className={linkClass} href={hrefFor(page + 1)}>
          Next
        </Link>
      ) : null}
    </nav>
  );
}
