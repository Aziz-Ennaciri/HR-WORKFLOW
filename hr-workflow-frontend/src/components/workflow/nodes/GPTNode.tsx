import { BaseNode } from "./BaseNode";

export function GPTNode({ data, selected }: any) {
  const icon = (
    <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
      <path
        d="M20.5 11.3c.1-.5.1-1 .1-1.5 0-2.8-1.7-5.3-4.3-6.4-.6-2.5-2.7-4.4-5.3-4.4-1.4 0-2.7.5-3.7 1.4C5.6.8 3.5 2.2 2.7 4.3.4 5.2-.6 7.7.3 10.1c-.2.5-.3 1-.3 1.6 0 2.8 1.7 5.3 4.3 6.4.6 2.5 2.7 4.4 5.3 4.4 1.4 0 2.7-.5 3.7-1.4 1.7.4 3.5-.2 4.7-1.5 1.2-1.3 1.6-3.1 1-4.8.9-.9 1.4-2.1 1.5-3.5z"
        fill="#8b5cf6"
        transform="translate(1.5, 1.5) scale(0.87)"
      />
      <path
        d="M10 8l4 4-4 4"
        stroke="white"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );

  const detail = data.config?.model
    ? `Model: ${data.config.model}`
    : data.config?.provider
      ? `Provider: ${data.config.provider}`
      : undefined;

  return (
    <BaseNode
      icon={icon}
      label="AI / LLM"
      subtitle="AI processing"
      accentColor="#8b5cf6"
      bgColor="#f5f3ff"
      borderColor="#ddd6fe"
      detail={detail}
      selected={selected}
    />
  );
}
