// The backend decides what the caller can see from the JWT (admins see all, others their own).
export function getWorkflowsUrl(): string {
  return "/workflows";
}
