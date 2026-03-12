"use client";

import Link from "next/link";
import { useState, useEffect } from "react";
import Button from "../ui/Button";
import { useRouter } from "next/navigation";

export default function Navbar() {
  const [loggedIn, setLoggedIn] = useState(false);
  const router = useRouter();

  useEffect(() => {
    setLoggedIn(!!localStorage.getItem("token"));
  }, []);

  const toggleDarkMode = () => {
    document.documentElement.classList.toggle("dark");
    const isDark = document.documentElement.classList.contains("dark");
    localStorage.setItem("theme", isDark ? "dark" : "light");
  };

  useEffect(() => {
    const theme = localStorage.getItem("theme");
    if (theme === "dark") {
      document.documentElement.classList.add("dark");
    }
  }, []);

  // if not authenticated, do not render the navigation bar
  if (!loggedIn) {
    return null;
  }

  const handleLogout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("user");
    router.push("/login");
    setLoggedIn(false);
  };

  return (
    <header className="bg-white shadow-md dark:bg-gray-900">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex items-center">
            <Link
              href="/"
              className="text-2xl font-bold text-primary dark:text-primary/80"
            >
              HR
              <span className="text-gray-800 dark:text-gray-100">Workflow</span>
            </Link>
          </div>
          <nav className="flex items-center space-x-4">
            {/* theme toggle */}
            <button
              onClick={toggleDarkMode}
              className="p-2 rounded-md hover:bg-gray-200 dark:hover:bg-gray-700 transition"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                className="h-5 w-5 text-gray-700 dark:text-gray-200"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 3v1m0 16v1m8.66-9h-1M4.34 12h-1m15.07 5.66l-.71-.71M6.34 6.34l-.71-.71m12.02 0l-.71.71M6.34 17.66l-.71.71M12 5a7 7 0 000 14 7 7 0 000-14z"
                />
              </svg>
            </button>
            <Link
              href="/"
              className="text-gray-700 hover:text-gray-900 dark:text-gray-200 dark:hover:text-white"
            >
              Home
            </Link>
            {loggedIn && (
              <>
                <Link
                  href="/dashboard"
                  className="text-gray-700 hover:text-gray-900 dark:text-gray-200 dark:hover:text-white"
                >
                  Dashboard
                </Link>
                <Link
                  href="/workflows"
                  className="text-gray-700 hover:text-gray-900 dark:text-gray-200 dark:hover:text-white"
                >
                  Workflows
                </Link>
              </>
            )}
            {!loggedIn ? (
              <Link href="/login">
                <Button variant="primary" className="px-4 py-2">
                  Login
                </Button>
              </Link>
            ) : (
              <Button
                onClick={handleLogout}
                variant="danger"
                className="px-4 py-2"
              >
                Logout
              </Button>
            )}
          </nav>
        </div>
      </div>
    </header>
  );
}
