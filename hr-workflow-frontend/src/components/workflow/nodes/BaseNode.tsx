import { Handle, Position } from "reactflow";
import { ReactNode } from "react";

interface BaseNodeProps {
  icon: ReactNode;
  label: string;
  subtitle: string;
  accentColor: string;
  bgColor: string;
  borderColor: string;
  detail?: string;
  selected?: boolean;
}

export function BaseNode({
  icon,
  label,
  subtitle,
  accentColor,
  bgColor,
  borderColor,
  detail,
  selected,
}: BaseNodeProps) {
  return (
    <div
      className={`
        relative bg-white rounded-[14px] w-[220px]
        transition-all duration-200
        ${selected ? "shadow-lg" : "shadow-md hover:shadow-lg"}
      `}
      style={{
        border: `1.5px solid ${selected ? accentColor : borderColor}`,
        boxShadow: selected ? `0 0 0 2px ${accentColor}33` : undefined,
      }}
    >
      {/* Left accent stripe */}
      <div
        className="absolute left-0 top-0 bottom-0 w-[3px] rounded-l-[13px]"
        style={{ background: accentColor }}
      />

      {/* Content */}
      <div className="px-4 py-3 pl-5">
        <div className="flex items-center gap-3">
          {/* Icon */}
          <div
            className="w-9 h-9 rounded-[10px] flex items-center justify-center shrink-0"
            style={{ background: bgColor }}
          >
            {icon}
          </div>

          {/* Text */}
          <div className="min-w-0">
            <div className="text-[13px] font-semibold text-gray-800 leading-tight">
              {label}
            </div>
            <div className="text-[11px] text-gray-400 leading-tight mt-0.5">
              {subtitle}
            </div>
          </div>
        </div>

        {/* Detail pill */}
        {detail && (
          <div className="mt-2.5 flex">
            <span
              className="inline-flex items-center text-[10px] font-medium px-2 py-0.5 rounded-full"
              style={{
                background: bgColor,
                color: accentColor,
              }}
            >
              {detail}
            </span>
          </div>
        )}
      </div>

      {/* Handles — Left (input) and Right (output) for horizontal flow */}
      <Handle
        type="target"
        position={Position.Left}
        style={{
          width: 10,
          height: 10,
          background: borderColor,
          border: "2px solid white",
          left: -5,
          cursor: "crosshair",
        }}
      />
      <Handle
        type="source"
        position={Position.Right}
        style={{
          width: 10,
          height: 10,
          background: borderColor,
          border: "2px solid white",
          right: -5,
          cursor: "crosshair",
        }}
      />
    </div>
  );
}
