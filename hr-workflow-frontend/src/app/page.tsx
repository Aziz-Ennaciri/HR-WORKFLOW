'use client'

import Link from 'next/link'

export default function Home() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-purple-50 to-blue-100">
      <div className="text-center">
        <h1 className="text-6xl font-bold mb-4 text-gray-800">
          HR Workflow System
        </h1>
        <p className="text-xl text-gray-600 mb-8">
          Automate your HR processes with ease
        </p>
        
        <div className="space-x-4">
          <Link 
            href="/login"
            className="inline-block bg-blue-600 text-white px-8 py-3 rounded-lg hover:bg-blue-700 transition font-medium"
          >
            Login
          </Link>
          
          <Link 
            href="/register"
            className="inline-block bg-gray-600 text-white px-8 py-3 rounded-lg hover:bg-gray-700 transition font-medium"
          >
            Register
          </Link>
        </div>
      </div>
    </div>
  )
}
