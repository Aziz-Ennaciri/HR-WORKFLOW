export default function Footer() {
  return (
    <footer className="bg-gray-900 text-gray-200">
      <div className="max-w-7xl mx-auto px-6 py-12 grid gap-8 md:grid-cols-3">
        <div>
          <h3 className="text-xl font-semibold">HR Workflow System</h3>
          <p className="mt-2 text-sm text-gray-400">
            Enterprise HR automation, approvals and onboarding in one platform.
          </p>
        </div>
        <div>
          <h4 className="font-semibold mb-2">Product</h4>
          <ul className="space-y-1 text-sm text-gray-400">
            <li>Workflow Builder</li>
            <li>Execution History</li>
            <li>Integrations</li>
          </ul>
        </div>
        <div>
          <h4 className="font-semibold mb-2">Company</h4>
          <ul className="space-y-1 text-sm text-gray-400">
            <li>About</li>
            <li>Terms</li>
            <li>Privacy</li>
          </ul>
        </div>
      </div>
      <div className="border-t border-gray-800 text-center py-4 text-sm text-gray-500">
        © {new Date().getFullYear()} HR Workflow System. All rights reserved.
      </div>
    </footer>
  );
}
