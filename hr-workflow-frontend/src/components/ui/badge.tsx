import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-semibold transition-colors",
  {
    variants: {
      tone: {
        neutral: "bg-muted text-muted-foreground",
        info: "bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300",
        success: "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300",
        warning: "bg-amber-100 text-amber-800 dark:bg-amber-500/15 dark:text-amber-300",
        danger: "bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-300",
      },
    },
    defaultVariants: { tone: "neutral" },
  },
);

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement>, VariantProps<typeof badgeVariants> {}

export function Badge({ className, tone, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ tone }), className)} {...props} />;
}

export type BadgeTone = NonNullable<VariantProps<typeof badgeVariants>["tone"]>;
