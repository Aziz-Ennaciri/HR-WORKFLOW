'use client'

import { useEffect, useState, useCallback } from 'react'
import { useParams, useRouter } from 'next/navigation'
import NodeConfigPanel from '@/components/workflow/NodeConfigPanel'
import { nodeTypes } from '@/components/workflow/nodes'
import ReactFlow, {
    Node,
    Edge,
    Controls,
    Background,
    applyNodeChanges,
    applyEdgeChanges,
    addEdge,
    NodeChange,
    EdgeChange,
    Connection,
} from 'reactflow'
import 'reactflow/dist/style.css'
import api from '@/lib/api'
import Sidebar from '@/components/layouts/sidebar'

export default function WorkflowDesignerPage() {
    const params = useParams()
    const router = useRouter()
    const [workflow, setWorkflow] = useState<any>(null)
    const [workflows, setWorkflows] = useState<any[]>([])
    const [nodes, setNodes] = useState<Node[]>([])
    const [edges, setEdges] = useState<Edge[]>([])
    const [loading, setLoading] = useState(true)
    const [selectedNode, setSelectedNode] = useState<Node | null>(null)

    useEffect(() => {
        fetchWorkflow()
        fetchWorkflows()
    }, [params.id])

    const onNodeClick = useCallback((_event: any, node: Node) => {
        setSelectedNode(node)
    }, [])

    const updateNodeConfig = (config: any) => {
        setNodes((nds) =>
            nds.map((node) =>
                node.id === selectedNode?.id
                    ? { ...node, data: { ...node.data, config } }
                    : node
            )
        )
    }

    const handleActivate = async () => {
        console.log('🔵 Activate clicked')
        console.log('📦 Workflow:', workflow)
        
        if (!workflow) {
            alert('Workflow not loaded')
            return
        }

        try {
            const requestData = {
                name: workflow.name,
                description: workflow.description,
                workflowKey: workflow.workflowKey,
                version: workflow.version,
                status: 'ACTIVE',
                createdById: workflow.createdById
            }
            
            console.log('📤 Sending:', requestData)
            
            const response = await api.put(`/workflows/${params.id}`, requestData)
            
            console.log('✅ Response:', response.data)
            alert('Workflow activated successfully!')
            await fetchWorkflow()
        } catch (error: any) {
            console.error('❌ Error:', error)
            console.error('❌ Response:', error.response?.data)
            alert(error.response?.data?.message || error.message || 'Failed')
        }
    }

    const handleDeactivate = async () => {
        try {
            await api.put(`/workflows/${params.id}`, {
                name: workflow.name,
                description: workflow.description,
                workflowKey: workflow.workflowKey,
                version: workflow.version,
                status: 'DRAFT',
                createdById: workflow.createdById
            })

            alert('Workflow deactivated!')
            await fetchWorkflow()
        } catch (error: any) {
            console.error('Failed to deactivate workflow:', error)
            alert(error.response?.data?.message || 'Failed to deactivate workflow')
        }
    }

    const fetchWorkflow = async () => {
        try {
            const response = await api.get(`/workflows/${params.id}`)
            console.log('📥 Fetched workflow:', response.data)
            setWorkflow(response.data)

            const nodesResponse = await api.get(`/nodes/workflow/${params.id}`)
            console.log('📥 Loaded nodes:', nodesResponse.data)

            if (nodesResponse.data && nodesResponse.data.length > 0) {
                const flowNodes = nodesResponse.data.map((node: any, index: number) => {
                    let config = {}
                    try {
                        config = JSON.parse(node.configJson || '{}')
                    } catch (e) {
                        console.error('Failed to parse config for node', node.id, e)
                    }

                    return {
                        id: node.id.toString(),
                        type: node.type.toLowerCase(),
                        position: {
                            x: 100 + (index * 250),
                            y: 100 + (Math.floor(index / 3) * 150)
                        },
                        data: {
                            label: node.type,
                            config: config,
                            order: node.order
                        },
                    }
                })

                setNodes(flowNodes)
            }
        } catch (error) {
            console.error('Failed to fetch workflow:', error)
        } finally {
            setLoading(false)
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

    const onNodesChange = useCallback(
        (changes: NodeChange[]) => setNodes((nds) => applyNodeChanges(changes, nds)),
        []
    )

    const onEdgesChange = useCallback(
        (changes: EdgeChange[]) => setEdges((eds) => applyEdgeChanges(changes, eds)),
        []
    )

    const onConnect = useCallback(
        (connection: Connection) => setEdges((eds) => addEdge(connection, eds)),
        []
    )

    const addNode = (type: string) => {
        const newNode: Node = {
            id: `${type}-${Date.now()}`,
            type: type.toLowerCase(),
            position: { x: 250, y: 100 + (nodes.length * 100) },
            data: {
                label: type,
                config: {},
                order: nodes.length + 1
            },
        }
        setNodes((nds) => [...nds, newNode])
    }

    const handleSave = async () => {
        try {
            const existingNodesResponse = await api.get(`/nodes/workflow/${params.id}`)
            for (const node of existingNodesResponse.data) {
                await api.delete(`/nodes/${node.id}`)
            }

            for (let i = 0; i < nodes.length; i++) {
                const node = nodes[i]
                await api.post(`/nodes/workflow/${params.id}`, {
                    type: node.data.label,
                    order: i + 1,
                    configJson: JSON.stringify(node.data.config || {})
                })
            }

            alert('Workflow saved successfully!')
            await fetchWorkflow()
        } catch (error: any) {
            console.error('Failed to save workflow:', error)
            alert(error.response?.data?.message || 'Failed to save workflow')
        }
    }

    if (loading) {
        return (
            <div className="flex h-screen">
                <Sidebar workflows={workflows} onCreateWorkflow={() => { }} />
                <div className="flex-1 flex items-center justify-center">
                    <div className="text-center">
                        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
                        <p className="mt-4 text-gray-600">Loading workflow...</p>
                    </div>
                </div>
            </div>
        )
    }

    return (
        <div className="flex h-screen">
            <Sidebar workflows={workflows} onCreateWorkflow={() => router.push('/dashboard')} />

            <div className="flex-1 flex flex-col">
                {/* Top Bar */}
                <div className="bg-white border-b px-6 py-4 flex items-center justify-between">
                    <div className="flex items-center space-x-4">
                        <button
                            onClick={() => router.push('/dashboard')}
                            className="text-gray-600 hover:text-gray-900"
                        >
                            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
                            </svg>
                        </button>
                        <div>
                            <h1 className="text-xl font-bold text-gray-900">{workflow?.name}</h1>
                            <p className="text-sm text-gray-500">{workflow?.description}</p>
                        </div>
                    </div>

                    <div className="flex items-center space-x-3">
                        <span className={`px-3 py-1 rounded-full text-xs font-medium ${
                            workflow?.status === 'ACTIVE'
                                ? 'bg-green-100 text-green-800'
                                : 'bg-yellow-100 text-yellow-800'
                        }`}>
                            {workflow?.status}
                        </span>

                        {workflow?.status === 'DRAFT' ? (
                            <button
                                onClick={handleActivate}
                                className="bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700 transition font-medium"
                            >
                                Activate
                            </button>
                        ) : (
                            <button
                                onClick={handleDeactivate}
                                className="bg-yellow-600 text-white px-4 py-2 rounded-lg hover:bg-yellow-700 transition font-medium"
                            >
                                Deactivate
                            </button>
                        )}

                        <button
                            onClick={handleSave}
                            className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 transition font-medium"
                        >
                            Save
                        </button>

                        <button
                            onClick={() => router.push(`/workflows/${params.id}/execute`)}
                            disabled={workflow?.status !== 'ACTIVE'}
                            className="bg-purple-600 text-white px-6 py-2 rounded-lg hover:bg-purple-700 transition font-medium disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            Execute
                        </button>
                    </div>
                </div>

                {/* Node Palette */}
                <div className="bg-gray-50 border-b px-6 py-3">
                    <div className="flex items-center space-x-2">
                        <span className="text-sm font-medium text-gray-700">Add Node:</span>
                        <button
                            onClick={() => addNode('EMAIL')}
                            className="px-4 py-2 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition text-sm font-medium flex items-center space-x-2"
                        >
                            <span>📧</span>
                            <span>Email</span>
                        </button>
                        <button
                            onClick={() => addNode('GPT')}
                            className="px-4 py-2 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition text-sm font-medium flex items-center space-x-2"
                        >
                            <span>🤖</span>
                            <span>GPT</span>
                        </button>
                        <button
                            onClick={() => addNode('DRIVE')}
                            className="px-4 py-2 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition text-sm font-medium flex items-center space-x-2"
                        >
                            <span>💾</span>
                            <span>Drive</span>
                        </button>
                        <button
                            onClick={() => addNode('EXCEL')}
                            className="px-4 py-2 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition text-sm font-medium flex items-center space-x-2"
                        >
                            <span>📊</span>
                            <span>Excel</span>
                        </button>
                    </div>
                </div>

                {/* React Flow Canvas */}
                <div className="flex-1 flex">
                    <div className="flex-1">
                        <ReactFlow
                            nodes={nodes}
                            edges={edges}
                            onNodesChange={onNodesChange}
                            onEdgesChange={onEdgesChange}
                            onConnect={onConnect}
                            onNodeClick={onNodeClick}
                            nodeTypes={nodeTypes}
                            fitView
                            deleteKeyCode="Delete"
                        >
                            <Background />
                            <Controls />
                        </ReactFlow>
                    </div>

                    {/* Config Panel */}
                    {selectedNode && (
                        <NodeConfigPanel
                            node={selectedNode}
                            onUpdate={updateNodeConfig}
                            onClose={() => setSelectedNode(null)}
                        />
                    )}
                </div>
            </div>
        </div>
    )
}