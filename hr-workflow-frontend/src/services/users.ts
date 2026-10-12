import api from "@/lib/api";
import type { LoginResponse, Role, Theme, User } from "@/types";

export const usersApi = {
  login: async (email: string, password: string) =>
    (await api.post<LoginResponse>("/users/login", { email, password })).data,

  register: async (body: { firstName: string; lastName: string; email: string; password: string }) =>
    (await api.post<User>("/users/register", body)).data,

  me: async () => (await api.get<User>("/users/me")).data,

  updateProfile: async (body: { firstName: string; lastName: string }) =>
    (await api.patch<User>("/users/me", body)).data,

  changeEmail: async (body: { newEmail: string; currentPassword: string }) =>
    (await api.put<User>("/users/me/email", body)).data,

  changePassword: async (body: { currentPassword: string; newPassword: string }) => {
    await api.put("/users/me/password", body);
  },

  updatePreferences: async (body: { theme?: Theme; emailNotificationsEnabled?: boolean }) =>
    (await api.put<User>("/users/me/preferences", body)).data,

  deleteMe: async () => {
    await api.delete("/users/me");
  },

  byRole: async (role: Role) => (await api.get<User[]>("/users/by-role", { params: { role } })).data,
};
