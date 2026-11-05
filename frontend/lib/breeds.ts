import type { Breed, Species } from "@/lib/types";

export const SPECIES_LABELS: Record<Species, string> = {
  DOG: "Dog",
  CAT: "Cat",
};

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
