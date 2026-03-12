import React from "react";

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "outline" | "danger";
}

const variantClasses: Record<string, string> = {
  primary: "bg-primary text-primary-foreground hover:bg-primary/90",
  secondary: "bg-secondary text-secondary-foreground hover:bg-secondary/90",
  outline:
    "bg-transparent border border-primary text-primary hover:bg-primary/10",
  danger: "bg-destructive text-destructive-foreground hover:bg-destructive/90",
};

export default function Button({
  variant = "primary",
  className = "",
  ...props
}: ButtonProps) {
  return (
    <button
      className={`px-4 py-2 rounded-lg font-semibold transition ${variantClasses[variant]} ${className}`}
      {...props}
    />
  );
}
