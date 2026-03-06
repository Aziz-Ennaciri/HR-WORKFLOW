'use client'

import { useState, useEffect } from 'react'

interface NodeConfigPanelProps {
  node: any
  onUpdate: (config: any) => void
  onClose: () => void
}

export default function NodeConfigPanel({ node, onUpdate, onClose }: NodeConfigPanelProps) {
  const [config, setConfig] = useState(node?.data?.config || {})

  useEffect(() => {
    setConfig(node?.data?.config || {})
  }, [node])

  if (!node) return null

  const handleSave = () => {
    onUpdate(config)
    onClose()
  }

  const renderConfigFields = () => {
    switch (node.data.label) {
      case 'EMAIL':
        return (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                To *
              </label>
              <input
                type="email"
                value={config.to || ''}
                onChange={(e) => setConfig({ ...config, to: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="recipient@example.com"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Subject
              </label>
              <input
                type="text"
                value={config.subject || ''}
                onChange={(e) => setConfig({ ...config, subject: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="Email subject"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Body
              </label>
              <textarea
                value={config.body || ''}
                onChange={(e) => setConfig({ ...config, body: e.target.value })}
                rows={4}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="Email message"
              />
            </div>
          </>
        )

      case 'GPT':
        return (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Model *
              </label>
              <select
                value={config.model || 'gpt-4'}
                onChange={(e) => setConfig({ ...config, model: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
                <option value="gpt-4">GPT-4</option>
                <option value="gpt-3.5-turbo">GPT-3.5 Turbo</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Prompt *
              </label>
              <textarea
                value={config.prompt || ''}
                onChange={(e) => setConfig({ ...config, prompt: e.target.value })}
                rows={4}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="Enter your prompt here..."
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Temperature
              </label>
              <input
                type="number"
                min="0"
                max="2"
                step="0.1"
                value={config.temperature || 0.7}
                onChange={(e) => setConfig({ ...config, temperature: parseFloat(e.target.value) })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
          </>
        )

      case 'DRIVE':
        return (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Action *
              </label>
              <select
                value={config.action || 'upload'}
                onChange={(e) => setConfig({ ...config, action: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
                <option value="upload">Upload File</option>
                <option value="download">Download File</option>
                <option value="create">Create Folder</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Folder ID
              </label>
              <input
                type="text"
                value={config.folderId || ''}
                onChange={(e) => setConfig({ ...config, folderId: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="Google Drive folder ID"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                File Name
              </label>
              <input
                type="text"
                value={config.fileName || ''}
                onChange={(e) => setConfig({ ...config, fileName: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="example.pdf"
              />
            </div>
          </>
        )

      case 'EXCEL':
        return (
          <>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Sheet Name *
              </label>
              <input
                type="text"
                value={config.sheetName || ''}
                onChange={(e) => setConfig({ ...config, sheetName: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="Sheet1"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Operation *
              </label>
              <select
                value={config.operation || 'READ'}
                onChange={(e) => setConfig({ ...config, operation: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
                <option value="READ">Read Data</option>
                <option value="WRITE">Write Data</option>
                <option value="UPDATE">Update Data</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Start Row
              </label>
              <input
                type="number"
                min="1"
                value={config.startRow || 1}
                onChange={(e) => setConfig({ ...config, startRow: parseInt(e.target.value) })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
          </>
        )

      default:
        return null
    }
  }

  return (
    <div className="w-96 bg-white border-l border-gray-200 h-full overflow-y-auto">
      <div className="p-6 border-b border-gray-200">
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-lg font-semibold text-gray-900">
            Configure {node.data.label}
          </h3>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <p className="text-sm text-gray-500">
          Configure the settings for this node
        </p>
      </div>

      <div className="p-6 space-y-4">
        {renderConfigFields()}

        <div className="pt-4 flex space-x-3">
          <button
            onClick={onClose}
            className="flex-1 bg-gray-200 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-300 transition font-medium"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            className="flex-1 bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 transition font-medium"
          >
            Save
          </button>
        </div>
      </div>

      {/* Node Info */}
      <div className="p-6 border-t border-gray-200 bg-gray-50">
        <h4 className="text-sm font-semibold text-gray-900 mb-2">About this node</h4>
        <p className="text-xs text-gray-600">
          {node.data.label === 'EMAIL' && 'Send email notifications to specified recipients'}
          {node.data.label === 'GPT' && 'Process text using OpenAI GPT models'}
          {node.data.label === 'DRIVE' && 'Interact with Google Drive files and folders'}
          {node.data.label === 'EXCEL' && 'Read, write, or update Excel spreadsheets'}
        </p>
      </div>
    </div>
  )
}