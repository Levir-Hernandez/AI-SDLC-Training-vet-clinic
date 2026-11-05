"use client";

import { useState } from "react";
import { ErrorMessage } from "@/components/ErrorMessage";
import { FormField } from "@/components/FormField";
import { breedsFor, SPECIES_LABELS } from "@/lib/breeds";
import type { Species } from "@/lib/types";

const selectClass =
  "rounded border border-zinc-300 px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900";

interface PetFormProps {
  errors?: Record<string, string>;
  required?: boolean;
}

/** Pet fields (petName, species, breed) for use inside a form. */
export function PetForm({ errors = {}, required = true }: PetFormProps) {
  const [species, setSpecies] = useState<Species | "">("");
  const [breed, setBreed] = useState("");
  const breeds = breedsFor(species);

  return (
    <div className="flex flex-col gap-4">
      <FormField
        name="petName"
        label="Pet name"
        required={required}
        error={errors.petName ?? errors.name}
      />
      <div className="flex flex-col gap-1">
        <label htmlFor="species" className="text-sm font-medium">
          Species
        </label>
        <select
          id="species"
          name="species"
          value={species}
          required={required}
          onChange={(e) => {
            setSpecies(e.target.value as Species | "");
            setBreed("");
          }}
          className={selectClass}
        >
          <option value="">Select species</option>
          {(Object.keys(SPECIES_LABELS) as Species[]).map((s) => (
            <option key={s} value={s}>
              {SPECIES_LABELS[s]}
            </option>
          ))}
        </select>
        <ErrorMessage message={errors.species} />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="breed" className="text-sm font-medium">
          Breed
        </label>
        <select
          id="breed"
          name="breed"
          value={breed}
          required={required}
          disabled={species === ""}
          onChange={(e) => setBreed(e.target.value)}
          className={selectClass}
        >
          <option value="">Select breed</option>
          {breeds.map((b) => (
            <option key={b.value} value={b.value}>
              {b.label}
            </option>
          ))}
        </select>
        <ErrorMessage message={errors.breed} />
      </div>
    </div>
  );
}
