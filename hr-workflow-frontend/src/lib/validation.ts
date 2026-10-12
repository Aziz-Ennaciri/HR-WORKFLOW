import { z } from "zod";

/** Matches the backend rule (CreateUserDTO / ChangePasswordDTO): at least 8 characters. */
export const passwordSchema = z.string().min(8, "Use at least 8 characters").max(128, "Use at most 128 characters");

export const emailSchema = z.string().trim().min(1, "Email is required").email("Enter a valid email address");
