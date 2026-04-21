import { Handle, Position } from "reactflow";

export function ApprovalNode({ data }: any) {
  return (
    <div className="bg-white border-2 border-orange-500 rounded-lg shadow-lg p-4 min-w-[200px]">
      <Handle type="target" position={Position.Top} />

      <div className="flex items-center space-x-3">
        <div className="bg-orange-100 p-2 rounded-lg">
          <span className="text-2xl">✋</span>
        </div>
        <div>
          <div className="font-semibold text-gray-900">Approval</div>
          <div className="text-xs text-gray-500">Human review gate</div>
        </div>
      </div>

      {data.config?.approverEmail && (
        <div className="mt-3 text-xs text-gray-600">
          Approver: {data.config.approverEmail}
        </div>
      )}
      {data.config?.instructions && (
        <div className="mt-1 text-xs text-gray-500 truncate max-w-[180px]">
          {data.config.instructions}
        </div>
      )}

      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}
