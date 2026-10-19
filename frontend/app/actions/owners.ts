"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { ApiError, apiPost } from "@/lib/api";
import type { CreatePetRequest, Owner, Pet } from "@/lib/types";

export type OwnerFormState = {
  error?: string;
  fieldErrors?: Record<string, string>;
};

function readPet(formData: FormData): CreatePetRequest {
  return {
    name: String(formData.get("petName") ?? "").trim(),
    species: String(formData.get("petSpecies") ?? "") as CreatePetRequest["species"],
    breed: String(formData.get("petBreed") ?? "") as CreatePetRequest["breed"],
  };
}

const isBlankPet = (p: CreatePetRequest) => p.name === "" && p.species === "" && p.breed === "";

const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

/** `pets[0].name` -> `petName`; owner fields keep their name. */
function ownerField(field: string): string {
  const match = /^pets\[\d+\]\.(\w+)$/.exec(field);
  return match ? `pet${capitalize(match[1])}` : field;
}

function mapError(error: unknown, toField: (field: string) => string): OwnerFormState {
  if (!(error instanceof ApiError)) throw error;
  if (error.code === "PHONE_TAKEN") {
    return { fieldErrors: { phone: "This phone number is already registered." } };
  }
  if (error.code === "BREED_SPECIES_MISMATCH") {
    return { fieldErrors: { petBreed: "The breed does not match the selected species." } };
  }
  if (error.status === 400 && error.fieldErrors.length > 0) {
    const fieldErrors: Record<string, string> = {};
    for (const e of error.fieldErrors) fieldErrors[toField(e.field)] = e.message;
    return { fieldErrors };
  }
  if (error.status === 403) return { error: "Only reception can register owners and pets." };
  return { error: error.message };
}

export async function createOwner(
  _previous: OwnerFormState,
  formData: FormData,
): Promise<OwnerFormState> {
  const pet = readPet(formData);
  try {
    await apiPost<Owner>("/api/owners", {
      name: String(formData.get("name") ?? "").trim(),
      phone: String(formData.get("phone") ?? "").trim(),
      password: String(formData.get("password") ?? ""),
      pets: isBlankPet(pet) ? [] : [pet],
    });
  } catch (error) {
    return mapError(error, ownerField);
  }
  revalidatePath("/reception/owners");
  redirect("/reception/owners");
}

export async function addPet(
  ownerId: number,
  _previous: OwnerFormState,
  formData: FormData,
): Promise<OwnerFormState> {
  try {
    await apiPost<Pet>(`/api/owners/${ownerId}/pets`, readPet(formData));
  } catch (error) {
    return mapError(error, (field) => `pet${capitalize(field)}`);
  }
  revalidatePath(`/reception/owners/${ownerId}`);
  redirect(`/reception/owners/${ownerId}`);
}
