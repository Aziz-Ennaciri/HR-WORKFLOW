import { getUser } from "@/lib/auth";

export function getWorkflowsUrl(): string {
  const user = getUser();
  if (!user?.id) return "/workflows?userId=0&role=ROLE_RH";
  const role = user.roles?.includes("ROLE_ADMIN") ? "ROLE_ADMIN" : "ROLE_RH";
  return `/workflows?userId=${user.id}&role=${role}`;
}

export function isAdmin(): boolean {
  const user = getUser();
  return user?.roles?.includes("ROLE_ADMIN") || false;
}

export function isWorkflowOwner(workflow: any): boolean {
  const user = getUser();
  if (!user?.id || !workflow) return false;
  return workflow.createdById === user.id;
}
