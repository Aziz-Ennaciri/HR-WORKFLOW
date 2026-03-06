'use client'

import { useEffect, useState } from 'react'
import { useParams, useRouter } from 'next/navigation'
import api from '@/lib/api'
import Sidebar from '@/components/layouts/sidebar'

export default function ExecuteWorkflowPage() {
  const params = useParams()
  const router = useRouter()
  const [workflow, setWorkflow] = useState<any>(null)
  const [workflows, setWorkflows] = useState<any[]>([])
  const [execution, setExecution] = useState<any>(null)
  const [inputData, setInputData] = useState('{}')
  const [loading, setLoading] = useState(false)
  const [executing, setExecuting] = useState(false)

  useEffect(() => {
    fetchWorkflow()
    fetchWorkflows()
  }, [params.id])

  const fetchWorkflow = async () => {
    try {
      const response = await api.get(`/workflows/${params.id}`)
      setWorkflow(response.data)
    } catch (error) {
      console.error('Failed to fetch workflow:', error)
    }
  }

  const fetchWorkflows = async () => {
    try {
      const response = await api.get('/workflows')
      setWorkflows(response.data)
    } catch (error) {
      console.error('Failed to fetch workflows:', error)
    }
  }

  const executeWorkflow = async () => {
  setExecuting(true)
  setLoading(true)

  try {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    
    console.log('Executing workflow:', {
      workflowId: parseInt(params.id as string),
      inputData: inputData,
      userId: user.id
    })
    
    // Match your backend TriggerWorkflowInstanceDTO format
    const response = await api.post(`/executions/trigger?userId=${user.id}`, {
      workflowId: parseInt(params.id as string),
      inputData: inputData  // Send as string, not parsed
    })

    console.log('Execution response:', response.data)
    setExecution(response.data)
    
    // Poll for execution details
    if (response.data.id) {
      setTimeout(() => fetchExecutionDetails(response.data.id), 2000)
    }
  } catch (error: any) {
    console.error('Failed to execute workflow:', error)
    console.error('Error details:', error.response?.data)
    alert(error.response?.data?.message || error.message || 'Failed to execute workflow')
    setExecuting(false)
    setLoading(false)
  }
}

  const fetchExecutionDetails = async (executionId: number) => {
    try {
      const response = await api.get(`/executions/${executionId}/detail`)
      setExecution(response.data)
      setExecuting(false)
      setLoading(false)
    } catch (error) {
      console.error('Failed to fetch execution details:', error)
      setExecuting(false)
      setLoading(false)
    }
  }

  return (
    <div className="flex h-screen">
      <Sidebar workflows={workflows} onCreateWorkflow={() => router.push('/dashboard')} />

      <div className="flex-1 overflow-y-auto bg-gray-50">
        <div className="max-w-5xl mx-auto px-8 py-8">
          {/* Header */}
          <div className="mb-8">
            <button
              onClick={() => router.push(`/workflows/${params.id}`)}
              className="text-blue-600 hover:text-blue-800 mb-4 flex items-center space-x-2"
            >
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
              </svg>
              <span>Back to Designer</span>
            </button>
            <h1 className="text-3xl font-bold text-gray-900">Execute Workflow</h1>
            <p className="text-gray-600 mt-1">{workflow?.name}</p>
          </div>

          {/* Input Data Card */}
          <div className="bg-white rounded-xl shadow-sm border p-6 mb-6">
            <h2 className="text-xl font-semibold text-gray-900 mb-4">Input Data (JSON)</h2>
            <textarea
              value={inputData}
              onChange={(e) => setInputData(e.target.value)}
              rows={6}
              className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono text-sm"
              placeholder='{"employeeName": "John Doe", "department": "Engineering"}'
            />
            <p className="text-sm text-gray-500 mt-2">
              Enter input data as valid JSON
            </p>
            {workflow?.status !== 'ACTIVE' && (
            <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 mb-4">
                <p className="text-yellow-800">
                ⚠️ This workflow is in <strong>{workflow?.status}</strong> status. 
                Only ACTIVE workflows can be executed.
                </p>
            </div>
            )}
            <button
                onClick={executeWorkflow}
                disabled={loading || workflow?.status !== 'ACTIVE'}
                className="mt-4 bg-green-600 text-white px-8 py-3 rounded-lg hover:bg-green-700 transition font-medium disabled:opacity-50 disabled:cursor-not-allowed flex items-center space-x-2"
            >
              {executing ? (
                <>
                  <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white"></div>
                  <span>Executing...</span>
                </>
              ) : (
                <>
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <span>Execute Workflow</span>
                </>
              )}
            </button>
          </div>

          {/* Execution Results */}
          {execution && (
            <div className="bg-white rounded-xl shadow-sm border overflow-hidden">
              <div className="p-6 border-b bg-gray-50">
                <div className="flex items-center justify-between">
                  <h2 className="text-xl font-semibold text-gray-900">Execution Results</h2>
                  <span className={`px-4 py-2 rounded-full text-sm font-semibold ${
                    execution.status === 'COMPLETED' 
                      ? 'bg-green-100 text-green-800'
                      : execution.status === 'FAILED'
                      ? 'bg-red-100 text-red-800'
                      : execution.status === 'RUNNING'
                      ? 'bg-blue-100 text-blue-800'
                      : 'bg-yellow-100 text-yellow-800'
                  }`}>
                    {execution.status}
                  </span>
                </div>
              </div>

              {/* Execution Info */}
              <div className="p-6 border-b bg-gray-50">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <p className="text-sm text-gray-600">Execution ID</p>
                    <p className="text-lg font-semibold text-gray-900">{execution.id}</p>
                  </div>
                  <div>
                    <p className="text-sm text-gray-600">Started At</p>
                    <p className="text-lg font-semibold text-gray-900">
                      {new Date(execution.startedAt).toLocaleString()}
                    </p>
                  </div>
                  {execution.finishedAt && (
                    <>
                      <div>
                        <p className="text-sm text-gray-600">Finished At</p>
                        <p className="text-lg font-semibold text-gray-900">
                          {new Date(execution.finishedAt).toLocaleString()}
                        </p>
                      </div>
                      <div>
                        <p className="text-sm text-gray-600">Duration</p>
                        <p className="text-lg font-semibold text-gray-900">
                          {execution.durationMs ? `${execution.durationMs}ms` : 'N/A'}
                        </p>
                      </div>
                    </>
                  )}
                </div>
              </div>

              {/* Node Instances */}
              {execution.nodeInstances && execution.nodeInstances.length > 0 && (
                <div className="p-6">
                  <h3 className="text-lg font-semibold text-gray-900 mb-4">Node Execution Details</h3>
                  <div className="space-y-4">
                    {execution.nodeInstances.map((nodeInstance: any, index: number) => (
                      <div
                        key={nodeInstance.id}
                        className={`border rounded-lg p-4 ${
                          nodeInstance.status === 'COMPLETED'
                            ? 'border-green-200 bg-green-50'
                            : nodeInstance.status === 'FAILED'
                            ? 'border-red-200 bg-red-50'
                            : 'border-gray-200 bg-gray-50'
                        }`}
                      >
                        <div className="flex items-center justify-between mb-3">
                          <div className="flex items-center space-x-3">
                            <span className="text-2xl">
                              {nodeInstance.node?.type === 'EMAIL' && '📧'}
                              {nodeInstance.node?.type === 'GPT' && '🤖'}
                              {nodeInstance.node?.type === 'DRIVE' && '💾'}
                              {nodeInstance.node?.type === 'EXCEL' && '📊'}
                            </span>
                            <div>
                              <h4 className="font-semibold text-gray-900">
                                {nodeInstance.node?.type || 'Unknown'} Node
                              </h4>
                              <p className="text-sm text-gray-600">
                                Execution Order: {nodeInstance.executionOrder}
                              </p>
                            </div>
                          </div>
                          <span className={`px-3 py-1 rounded-full text-xs font-semibold ${
                            nodeInstance.status === 'COMPLETED'
                              ? 'bg-green-100 text-green-800'
                              : nodeInstance.status === 'FAILED'
                              ? 'bg-red-100 text-red-800'
                              : 'bg-gray-100 text-gray-800'
                          }`}>
                            {nodeInstance.status}
                          </span>
                        </div>

                        {nodeInstance.outputData && (
                          <div className="mt-3">
                            <p className="text-sm font-medium text-gray-700 mb-1">Output:</p>
                            <pre className="bg-white p-3 rounded border text-xs overflow-x-auto">
                              {nodeInstance.outputData}
                            </pre>
                          </div>
                        )}

                        {nodeInstance.errorMessage && (
                          <div className="mt-3">
                            <p className="text-sm font-medium text-red-700 mb-1">Error:</p>
                            <pre className="bg-red-50 p-3 rounded border border-red-200 text-xs text-red-800 overflow-x-auto">
                              {nodeInstance.errorMessage}
                            </pre>
                          </div>
                        )}

                        <div className="mt-3 flex items-center space-x-4 text-xs text-gray-600">
                          {nodeInstance.startedAt && (
                            <span>Started: {new Date(nodeInstance.startedAt).toLocaleTimeString()}</span>
                          )}
                          {nodeInstance.finishedAt && (
                            <span>Finished: {new Date(nodeInstance.finishedAt).toLocaleTimeString()}</span>
                          )}
                          {nodeInstance.durationMs && (
                            <span>Duration: {nodeInstance.durationMs}ms</span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Overall Output */}
              {execution.outputData && (
                <div className="p-6 border-t bg-gray-50">
                  <h3 className="text-lg font-semibold text-gray-900 mb-2">Final Output</h3>
                  <pre className="bg-white p-4 rounded border text-sm overflow-x-auto">
                    {execution.outputData}
                  </pre>
                </div>
              )}

              {/* Error */}
              {execution.errorMessage && (
                <div className="p-6 border-t bg-red-50">
                  <h3 className="text-lg font-semibold text-red-900 mb-2">Error</h3>
                  <pre className="bg-white p-4 rounded border border-red-200 text-sm text-red-800 overflow-x-auto">
                    {execution.errorMessage}
                  </pre>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}