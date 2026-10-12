"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { LogOut } from "lucide-react";
import { toast } from "sonner";
import { SettingsSection } from "@/components/settings/settings-section";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { logout } from "@/lib/api";
import { clearAuth } from "@/lib/auth";
import { getErrorMessage } from "@/lib/errors";
import { formatDateTime } from "@/lib/format";
import { usersApi } from "@/services/users";
import type { User } from "@/types";

export function SessionSection({ user }: { user: User }) {
  const router = useRouter();
  const [signingOut, setSigningOut] = useState(false);

  return (
    <SettingsSection title="Session" description={`Last sign-in: ${formatDateTime(user.lastLoginAt)}`}>
      <Button
        variant="outline"
        loading={signingOut}
        onClick={async () => {
          setSigningOut(true);
          await logout();
          router.push("/login");
        }}
      >
        <LogOut /> Sign out
      </Button>
    </SettingsSection>
  );
}

export function DeleteAccountSection() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [confirmation, setConfirmation] = useState("");
  const [deleting, setDeleting] = useState(false);

  const deleteAccount = async () => {
    setDeleting(true);
    try {
      await usersApi.deleteMe();
      clearAuth();
      toast.success("Your account has been deleted");
      router.push("/login");
    } catch (error) {
      toast.error(getErrorMessage(error, "Could not delete your account"));
      setDeleting(false);
    }
  };

  return (
    <SettingsSection title="Delete account" description="Disables your account and signs you out everywhere. Your workflows stay for your organisation.">
      <Button variant="destructive" onClick={() => setOpen(true)}>
        Delete my account
      </Button>
      <Dialog
        open={open}
        onOpenChange={(next) => {
          if (deleting) return;
          setOpen(next);
          setConfirmation("");
        }}
      >
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Delete your account?</DialogTitle>
            <DialogDescription>You will be signed out and won&apos;t be able to sign in again.</DialogDescription>
          </DialogHeader>
          <Field id="delete-confirmation" label='Type "DELETE" to confirm'>
            <Input autoComplete="off" value={confirmation} onChange={(e) => setConfirmation(e.target.value)} />
          </Field>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)} disabled={deleting}>
              Cancel
            </Button>
            <Button variant="destructive" disabled={confirmation !== "DELETE"} loading={deleting} onClick={deleteAccount}>
              Delete account
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </SettingsSection>
  );
}
