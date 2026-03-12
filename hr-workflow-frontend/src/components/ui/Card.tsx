import React from "react";

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {}

export default function Card({
  className = "",
  children,
  ...props
}: CardProps) {
  return (
    <div
      className={`p-6 border rounded-lg shadow-sm hover:shadow-md transition bg-white dark:bg-gray-700 ${className}`}
      {...props}
    >
      {children}
    </div>
  );
}
