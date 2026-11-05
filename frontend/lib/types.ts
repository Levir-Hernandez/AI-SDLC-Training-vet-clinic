export type Role = "OWNER" | "RECEPTIONIST" | "VETERINARIAN";

export interface CurrentUser {
  userId: number;
  username: string;
  role: Role;
  displayName: string;
  ownerId: number | null;
  veterinarianId: number | null;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  role: Role;
  displayName: string;
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ProblemDetail {
  status: number;
  title?: string;
  detail?: string;
  code?: string;
  errors?: FieldError[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

export type Weekday =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

export interface Service {
  id: number;
  code: string;
  name: string;
  durationMinutes: number;
  basePrice: number;
}

export interface ScheduleBlock {
  day: Weekday;
  start: string;
  end: string;
}

export interface Veterinarian {
  id: number;
  name: string;
  specialization: string;
  costPerService: number;
  schedule: ScheduleBlock[];
}

export type Species = "DOG" | "CAT";

export type Breed =
  | "LABRADOR_RETRIEVER"
  | "GERMAN_SHEPHERD"
  | "GOLDEN_RETRIEVER"
  | "BULLDOG"
  | "DOG_MIXED"
  | "SIAMESE"
  | "PERSIAN"
  | "MAINE_COON"
  | "BENGAL"
  | "CAT_MIXED";

export interface Pet {
  id: number;
  name: string;
  species: Species;
  breed: Breed;
  ownerId: number;
}

export interface Owner {
  id: number;
  name: string;
  phone: string;
  pets: Pet[] | null;
}

export interface CreatePetRequest {
  name: string;
  species: Species;
  breed: Breed;
}

export interface CreateOwnerRequest {
  name: string;
  phone: string;
  password: string;
  pets: CreatePetRequest[];
}

export type AppointmentStatus =
  | "RESERVED"
  | "SCHEDULED"
  | "IN_PROGRESS"
  | "FINISHED"
  | "PAID"
  | "CLOSED"
  | "RESCHEDULED"
  | "CANCELLED";

export type AppointmentAction =
  | "CONFIRM"
  | "CANCEL"
  | "EXPIRE"
  | "START"
  | "FINISH"
  | "RESCHEDULE"
  | "PAY"
  | "CLOSE"
  | "EDIT_OBSERVATIONS";

export interface Slot {
  veterinarianId: number;
  veterinarianName: string;
  startAt: string;
  endAt: string;
}

export interface Appointment {
  id: number;
  pet: { id: number; name: string; species: Species; breed: Breed };
  ownerName: string;
  service: { id: number; name: string; durationMinutes: number };
  veterinarian: { id: number; name: string };
  startAt: string;
  endAt: string;
  status: AppointmentStatus;
  bookedBy: "OWNER" | "RECEPTIONIST" | "VETERINARIAN" | "SYSTEM";
  total: number;
  observations: string | null;
  cancelReason: string | null;
  reservedUntil: string | null;
  rescheduledFromId: number | null;
  rescheduledToId: number | null;
  debt: boolean;
  upcoming: boolean;
  allowedActions: AppointmentAction[];
}

export interface CreateAppointmentRequest {
  petId: number;
  serviceId: number;
  veterinarianId: number;
  startAt: string;
}

export interface CreateVeterinarianRequest {
  name: string;
  specialization: string;
  costPerService: number;
  username: string;
  password: string;
  schedule: ScheduleBlock[];
}
