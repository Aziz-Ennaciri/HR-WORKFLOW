import { BaseNode } from "./BaseNode";

export function ExcelNode({ id, data, selected }: any) {
  const icon = (
    <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
      <rect
        x="3"
        y="3"
        width="18"
        height="18"
        rx="3"
        fill="#ea580c"
        opacity="0.15"
      />
      <rect
        x="3"
        y="3"
        width="18"
        height="18"
        rx="3"
        stroke="#ea580c"
        strokeWidth="1.5"
        fill="none"
      />
      <line x1="3" y1="9" x2="21" y2="9" stroke="#ea580c" strokeWidth="1.2" />
      <line x1="3" y1="15" x2="21" y2="15" stroke="#ea580c" strokeWidth="1.2" />
      <line x1="9" y1="3" x2="9" y2="21" stroke="#ea580c" strokeWidth="1.2" />
      <line x1="15" y1="3" x2="15" y2="21" stroke="#ea580c" strokeWidth="1.2" />
    </svg>
  );

  const detail = data.config?.fileName
    ? data.config.operation === "READ"
      ? `Read: ${data.config.fileName}`
      : `Report: ${data.config.fileName}`
    : undefined;

  return (
    <BaseNode
      id={id}
      icon={icon}
      label="Excel"
      subtitle="Spreadsheet"
      accentColor="#ea580c"
      bgColor="#fff7ed"
      borderColor="#fed7aa"
      detail={detail}
      selected={selected}
    />
  );
}
