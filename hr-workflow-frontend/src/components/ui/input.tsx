import * as React from "react";
import { cn } from "@/lib/utils";

const fieldClasses =
  "flex w-full rounded-lg border border-input bg-background px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring/40 focus-visible:border-ring disabled:cursor-not-allowed disabled:opacity-50 aria-[invalid=true]:border-destructive aria-[invalid=true]:ring-destructive/20";

const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, type, ...props }, ref) => (
    <input type={type} className={cn(fieldClasses, "h-9", className)} ref={ref} {...props} />
  ),
);
Input.displayName = "Input";

const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => (
    <textarea className={cn(fieldClasses, "min-h-[72px] resize-y", className)} ref={ref} {...props} />
  ),
);
Textarea.displayName = "Textarea";

/** A styled native select: accessible and mobile-friendly without extra JS. */
const Select = React.forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className, children, ...props }, ref) => (
    <select className={cn(fieldClasses, "h-9 cursor-pointer pr-8", className)} ref={ref} {...props}>
      {children}
    </select>
  ),
);
Select.displayName = "Select";

export { Input, Textarea, Select };
