import { Handle, Position } from 'reactflow'

export function EmailNode({ data }: any) {
  return (
    <div className="bg-white border-2 border-blue-500 rounded-lg shadow-lg p-4 min-w-[200px]">
      <Handle type="target" position={Position.Top} />
      
      <div className="flex items-center space-x-3">
        <div className="bg-blue-100 p-2 rounded-lg">
          <span className="text-2xl">📧</span>
        </div>
        <div>
          <div className="font-semibold text-gray-900">Email</div>
          <div className="text-xs text-gray-500">Send email notification</div>
        </div>
      </div>

      {data.config?.to && (
        <div className="mt-3 text-xs text-gray-600">
          To: {data.config.to}
        </div>
      )}
      
      <Handle type="source" position={Position.Bottom} />
    </div>
  )
}