import { isAxiosError } from "axios";

interface ProblemDetail {
  detail?: string;
  title?: string;
  errors?: Record<string, string>;
}

/**
 * A human-readable message for any error thrown by an API call. The backend answers with
 * RFC 7807 problem details; validation errors list one message per field.
 */
export function getErrorMessage(error: unknown, fallback = "Something went wrong"): string {
  if (isAxiosError<ProblemDetail>(error)) {
    const body = error.response?.data;
    if (body?.errors && Object.keys(body.errors).length > 0) {
      return Object.values(body.errors).join(". ");
    }
    if (body?.detail) return body.detail;
    if (!error.response) return "Cannot reach the server. Check your connection.";
  }
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}

export function getErrorStatus(error: unknown): number | undefined {
  return isAxiosError(error) ? error.response?.status : undefined;
}
