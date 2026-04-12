export default function Footer() {
  return (
    <footer className="bg-gray-900">
      <div className="max-w-7xl mx-auto px-6 py-12 grid gap-8 md:grid-cols-3">
        <div>
          <div className="flex items-center gap-2 mb-3">
            <div className="w-7 h-7 bg-blue-600 rounded-lg flex items-center justify-center">
              <span className="text-white font-bold text-xs">HR</span>
            </div>
            <h3 className="text-base font-semibold text-white">HR Workflow</h3>
          </div>
          <p className="text-sm text-gray-500 leading-relaxed">
            Enterprise HR automation, approvals and onboarding in one platform.
          </p>
        </div>
        <div>
          <h4 className="font-semibold text-white text-sm mb-3">Product</h4>
          <ul className="space-y-2 text-sm text-gray-500">
            <li className="hover:text-gray-300 cursor-pointer transition-colors">
              Workflow Builder
            </li>
            <li className="hover:text-gray-300 cursor-pointer transition-colors">
              Execution History
            </li>
            <li className="hover:text-gray-300 cursor-pointer transition-colors">
              Integrations
            </li>
          </ul>
        </div>
        <div>
          <h4 className="font-semibold text-white text-sm mb-3">Company</h4>
          <ul className="space-y-2 text-sm text-gray-500">
            <li className="hover:text-gray-300 cursor-pointer transition-colors">
              About
            </li>
            <li className="hover:text-gray-300 cursor-pointer transition-colors">
              Terms
            </li>
            <li className="hover:text-gray-300 cursor-pointer transition-colors">
              Privacy
            </li>
          </ul>
        </div>
      </div>
      <div className="border-t border-gray-800 text-center py-4 text-xs text-gray-600">
        © {new Date().getFullYear()} HR Workflow System. All rights reserved.
      </div>
    </footer>
  );
}
