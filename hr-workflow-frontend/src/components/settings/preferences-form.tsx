"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Monitor, Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import { toast } from "sonner";
import { SettingsSection } from "@/components/settings/settings-section";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { queryKeys } from "@/hooks/queries";
import { getErrorMessage } from "@/lib/errors";
import { cn } from "@/lib/utils";
import { usersApi } from "@/services/users";
import type { Theme, User } from "@/types";

const THEMES: { value: Theme; label: string; icon: typeof Sun }[] = [
  { value: "LIGHT", label: "Light", icon: Sun },
  { value: "DARK", label: "Dark", icon: Moon },
  { value: "SYSTEM", label: "System", icon: Monitor },
];

/** Preferences save as soon as they change; the theme applies immediately. */
export function PreferencesForm({ user }: { user: User }) {
  const queryClient = useQueryClient();
  const { setTheme } = useTheme();

  const save = async (changes: { theme?: Theme; emailNotificationsEnabled?: boolean }) => {
    const previous = user;
    queryClient.setQueryData(queryKeys.me, { ...user, ...changes });
    if (changes.theme) setTheme(changes.theme.toLowerCase());
    try {
      queryClient.setQueryData(queryKeys.me, await usersApi.updatePreferences(changes));
      toast.success("Preferences saved");
    } catch (error) {
      queryClient.setQueryData(queryKeys.me, previous);
      if (changes.theme) setTheme(previous.theme.toLowerCase());
      toast.error(getErrorMessage(error, "Could not save preferences"));
    }
  };

  return (
    <div className="space-y-5">
      <SettingsSection title="Appearance" description="Saved to your account, so it follows you across devices.">
        <div role="radiogroup" aria-label="Theme" className="grid grid-cols-3 gap-3">
          {THEMES.map(({ value, label, icon: Icon }) => {
            const selected = user.theme === value;
            return (
              <button
                key={value}
                type="button"
                role="radio"
                aria-checked={selected}
                onClick={() => !selected && save({ theme: value })}
                className={cn(
                  "flex flex-col items-center gap-2 rounded-lg border px-3 py-4 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                  selected ? "border-primary bg-primary/10 text-primary" : "hover:bg-accent",
                )}
              >
                <Icon className="h-5 w-5" aria-hidden />
                {label}
              </button>
            );
          })}
        </div>
      </SettingsSection>

      <SettingsSection title="Notifications">
        <div className="flex items-center justify-between gap-4">
          <div>
            <Label htmlFor="email-alerts">Workflow execution alerts</Label>
            <p id="email-alerts-hint" className="mt-1 text-xs text-muted-foreground">
              Email me when a workflow I triggered completes or fails.
            </p>
          </div>
          <Switch
            id="email-alerts"
            aria-describedby="email-alerts-hint"
            checked={user.emailNotificationsEnabled}
            onCheckedChange={(checked) => save({ emailNotificationsEnabled: checked })}
          />
        </div>
      </SettingsSection>
    </div>
  );
}
