import { BaseNode } from "./BaseNode";

export function EmailNode({ id, data, selected }: any) {
  const icon = (
    <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
      <rect
        x="2"
        y="4"
        width="20"
        height="16"
        rx="3"
        stroke="#2563eb"
        strokeWidth="1.8"
      />
      <path
        d="M2 7l8.9 5.17a2 2 0 002.2 0L22 7"
        stroke="#2563eb"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );

  const detail = data.config?.to ? `To: ${data.config.to}` : undefined;

  return (
    <BaseNode
      id={id}
      icon={icon}
      label="Email"
      subtitle="Send notification"
      accentColor="#2563eb"
      bgColor="#eff6ff"
      borderColor="#bfdbfe"
      detail={detail}
      selected={selected}
    />
  );
}
