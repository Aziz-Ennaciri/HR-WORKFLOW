import { AppShell } from "@/components/layout/app-shell";

/** Every signed-in page shares the sidebar, auth guard and "New workflow" dialog. */
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return <AppShell>{children}</AppShell>;
}
