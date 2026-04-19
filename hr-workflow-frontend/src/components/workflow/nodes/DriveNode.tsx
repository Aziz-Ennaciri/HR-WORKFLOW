import { BaseNode } from "./BaseNode";

export function DriveNode({ id, data, selected }: any) {
  const icon = (
    <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
      <path
        d="M7.71 3.5L1.15 15l3.43 5.92h6.85L7.71 3.5zm1.14 0l3.72 6.43L16.29 3.5H8.85zm8.57 0l-3.72 6.43 3.43 5.93 3.72-6.44L17.42 3.5zM12 10.93L8.57 16.86h6.86L12 10.93zm-3.43 6.93H1.72L5.15 24h6.85l-3.43-6.14zm10.29 0h-6.86l3.43 6.14h6.86l-3.43-6.14z"
        fill="#16a34a"
        transform="scale(0.85) translate(2, 2)"
      />
    </svg>
  );

  const detail = data.config?.action
    ? `Action: ${data.config.action}`
    : undefined;

  return (
    <BaseNode
      id={id}
      icon={icon}
      label="Drive"
      subtitle="File storage"
      accentColor="#16a34a"
      bgColor="#f0fdf4"
      borderColor="#bbf7d0"
      detail={detail}
      selected={selected}
    />
  );
}
