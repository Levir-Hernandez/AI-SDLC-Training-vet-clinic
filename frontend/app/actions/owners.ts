"use server";

import { revalidatePath } from "next/cache";
import { ApiError, apiPost } from "@/lib/api";
import type { Owner, Pet } from "@/lib/types";

export interface OwnerActionState {
  error?: string;
  success?: boolean;
  fieldErrors?: Record<string, string>;
}

function toState(error: unknown, fallback: string, prefix: string): OwnerActionState {
  if (error instanceof ApiError) {
    if (error.status === 400 && error.fieldErrors.length > 0) {
      const fieldErrors: Record<string, string> = {};
      for (const e of error.fieldErrors) {
        const key = e.field.replace(/^pets\[\d+\]\./, "");
        fieldErrors[e.field.startsWith("pets[") && key === "name" ? "petName" : key] =
          e.message;
      }
      return { error: "Please fix the highlighted fields.", fieldErrors };
    }
    if (error.status === 409) {
      return { error: "That phone number is already registered." };
    }
    if (error.status === 400) return { error: error.message };
    if (error.status === 403) return { error: `You are not allowed to ${prefix}.` };
    if (error.status === 404) return { error: "Owner not found." };
  }
  return { error: fallback };
}

function readPet(formData: FormData) {
  return {
    name: String(formData.get("petName") ?? ""),
    species: String(formData.get("species") ?? "") || null,
    breed: String(formData.get("breed") ?? "") || null,
  };
}

export async function createOwner(
  _prev: OwnerActionState,
  formData: FormData,
): Promise<OwnerActionState> {
  const pet = readPet(formData);
  const hasPet = pet.name.trim() !== "" || pet.species !== null || pet.breed !== null;
  const body = {
    name: String(formData.get("name") ?? ""),
    phone: String(formData.get("phone") ?? ""),
    password: String(formData.get("password") ?? ""),
    pets: hasPet ? [pet] : [],
  };
  try {
    await apiPost<Owner>("/api/owners", body);
  } catch (error) {
    return toState(error, "Could not register the owner. Try again later.", "register owners");
  }
  revalidatePath("/reception/owners");
  return { success: true };
}

export async function addPet(
  _prev: OwnerActionState,
  formData: FormData,
): Promise<OwnerActionState> {
  const ownerId = String(formData.get("ownerId") ?? "");
  try {
    await apiPost<Pet>(`/api/owners/${encodeURIComponent(ownerId)}/pets`, readPet(formData));
  } catch (error) {
    return toState(error, "Could not add the pet. Try again later.", "add pets");
  }
  revalidatePath(`/reception/owners/${ownerId}`);
  return { success: true };
}
