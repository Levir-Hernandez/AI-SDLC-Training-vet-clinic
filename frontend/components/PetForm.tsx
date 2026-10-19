"use client";

import { useState } from "react";
import { FormField } from "@/components/FormField";
import { SPECIES_OPTIONS, breedsFor } from "@/lib/breeds";
import type { Species } from "@/lib/types";

const CONTROL = "rounded border border-zinc-300 bg-transparent px-3 py-2 dark:border-zinc-700";

type Props = {
  /** Prefix for input names/ids so the fields can share a form with others. */
  prefix?: string;
  errors?: { name?: string; species?: string; breed?: string };
};

/** Pet fields: the breed select only offers breeds of the chosen species. */
export function PetFields({ prefix = "pet", errors }: Props) {
  const [species, setSpecies] = useState<Species | "">("");
  const [breed, setBreed] = useState("");
  const breeds = breedsFor(species);

  return (
    <div className="grid gap-4 sm:grid-cols-3">
      <FormField label="Pet name" name={`${prefix}Name`} error={errors?.name} />
      <div className="flex flex-col gap-1">
        <label htmlFor={`${prefix}Species`} className="text-sm font-medium">
          Species
        </label>
        <select
          id={`${prefix}Species`}
          name={`${prefix}Species`}
          value={species}
          onChange={(e) => {
            setSpecies(e.target.value as Species | "");
            setBreed("");
          }}
          className={CONTROL}
        >
          <option value="">Select species</option>
          {SPECIES_OPTIONS.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </select>
        {errors?.species ? <p className="text-sm text-red-600">{errors.species}</p> : null}
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor={`${prefix}Breed`} className="text-sm font-medium">
          Breed
        </label>
        <select
          id={`${prefix}Breed`}
          name={`${prefix}Breed`}
          value={breed}
          disabled={species === ""}
          onChange={(e) => setBreed(e.target.value)}
          className={`${CONTROL} disabled:opacity-60`}
        >
          <option value="">{species === "" ? "Select a species first" : "Select breed"}</option>
          {breeds.map((b) => (
            <option key={b.value} value={b.value}>
              {b.label}
            </option>
          ))}
        </select>
        {errors?.breed ? <p className="text-sm text-red-600">{errors.breed}</p> : null}
      </div>
    </div>
  );
}
