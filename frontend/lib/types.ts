// API types mirroring the backend DTOs. Keep this the single place for them.

export type Role = "OWNER" | "RECEPTIONIST" | "VETERINARIAN";

export type CurrentUser = {
  userId: number;
  username: string;
  role: Role;
  displayName: string;
};

export type LoginResponse = {
  token: string;
  expiresAt: string;
  role: Role;
  displayName: string;
};

export type FieldError = { field: string; message: string };

/** RFC 9457 problem detail returned by the backend, with its `code` and per-field `errors`. */
export type ProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  errors?: FieldError[];
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
};

export type Weekday =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

export type Service = {
  id: number;
  code: string;
  name: string;
  durationMinutes: number;
  basePrice: number;
};

/** `start` and `end` arrive as "HH:mm" or "HH:mm:ss". */
export type ScheduleBlock = {
  day: Weekday;
  start: string;
  end: string;
};

export type Veterinarian = {
  id: number;
  name: string;
  specialization: string;
  costPerService: number;
  schedule: ScheduleBlock[];
};

export type CreateVeterinarianRequest = {
  name: string;
  specialization: string;
  costPerService: number | null;
  username: string;
  password: string;
  schedule: ScheduleBlock[];
};

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

export type Pet = {
  id: number;
  name: string;
  species: Species;
  breed: Breed;
  ownerId: number;
};

export type Owner = {
  id: number;
  name: string;
  phone: string;
  /** Empty in list responses; filled in the detail response. */
  pets: Pet[];
};

export type CreatePetRequest = {
  name: string;
  species: Species | "";
  breed: Breed | "";
};

export type CreateOwnerRequest = {
  name: string;
  phone: string;
  password: string;
  pets: CreatePetRequest[];
};

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

export type Slot = {
  veterinarianId: number;
  veterinarianName: string;
  startAt: string;
  endAt: string;
};

export type Appointment = {
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
};

export type CreateAppointmentRequest = {
  petId: number;
  serviceId: number;
  veterinarianId: number;
  startAt: string;
};
