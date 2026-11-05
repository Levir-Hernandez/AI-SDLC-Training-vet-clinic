import type { InputHTMLAttributes } from "react";
import { ErrorMessage } from "./ErrorMessage";

interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  name: string;
  label: string;
  error?: string;
}

export function FormField({ name, label, error, ...rest }: FormFieldProps) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={name} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={name}
        name={name}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${name}-error` : undefined}
        className="rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
        {...rest}
      />
      <div id={`${name}-error`}>
        <ErrorMessage message={error} />
      </div>
    </div>
  );
}
