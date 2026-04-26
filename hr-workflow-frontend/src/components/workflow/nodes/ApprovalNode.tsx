import { BaseNode } from "./BaseNode";

export function ApprovalNode({ id, data, selected }: any) {
  const icon = (
    <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
      <path
        d="M9 12l2 2 4-4"
        stroke="#d97706"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2z"
        stroke="#d97706"
        strokeWidth="1.8"
        fill="none"
      />
      <path
        d="M15 9l-1.5 1.5"
        stroke="#d97706"
        strokeWidth="2"
        strokeLinecap="round"
        opacity="0.5"
      />
    </svg>
  );

  const detail = data.config?.approverEmail
    ? `Approver: ${data.config.approverEmail}`
    : undefined;

  return (
    <BaseNode
      id={id}
      icon={icon}
      label="Approval"
      subtitle="Human review gate"
      accentColor="#d97706"
      bgColor="#fffbeb"
      borderColor="#fde68a"
      detail={detail}
      selected={selected}
    />
  );
}
