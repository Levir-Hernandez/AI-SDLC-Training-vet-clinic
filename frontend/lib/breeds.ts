import type { Breed, Species } from "./types";

export const SPECIES_OPTIONS: { value: Species; label: string }[] = [
  { value: "DOG", label: "Dog" },
  { value: "CAT", label: "Cat" },
];

/** Mirrors the backend `Breed` enum: five breeds per species. */
export const BREEDS_BY_SPECIES: Record<Species, { value: Breed; label: string }[]> = {
  DOG: [
    { value: "LABRADOR_RETRIEVER", label: "Labrador Retriever" },
    { value: "GERMAN_SHEPHERD", label: "German Shepherd" },
    { value: "GOLDEN_RETRIEVER", label: "Golden Retriever" },
    { value: "BULLDOG", label: "Bulldog" },
    { value: "DOG_MIXED", label: "Mixed breed" },
  ],
  CAT: [
    { value: "SIAMESE", label: "Siamese" },
    { value: "PERSIAN", label: "Persian" },
    { value: "MAINE_COON", label: "Maine Coon" },
    { value: "BENGAL", label: "Bengal" },
    { value: "CAT_MIXED", label: "Mixed breed" },
  ],
};

export function breedsFor(species: Species | "") {
  return species === "" ? [] : BREEDS_BY_SPECIES[species];
}

export function breedLabel(breed: Breed): string {
  for (const list of Object.values(BREEDS_BY_SPECIES)) {
    const found = list.find((b) => b.value === breed);
    if (found) return found.label;
  }
  return breed;
}

export function speciesLabel(species: Species): string {
  return SPECIES_OPTIONS.find((s) => s.value === species)?.label ?? species;
}
