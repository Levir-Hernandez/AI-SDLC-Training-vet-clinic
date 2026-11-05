import type { Metadata } from "next";
import { Suspense } from "react";
import { AppHeader } from "@/components/AppHeader";
import { getCurrentUser } from "@/lib/auth";
import "./globals.css";

export const metadata: Metadata = {
  title: "Vet Clinic",
  description: "Veterinary clinic management",
};

// Renders nothing when signed out (e.g. on /login).
async function SessionHeader() {
  const user = await getCurrentUser();
  return user ? <AppHeader user={user} /> : null;
}

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full flex flex-col">
        <Suspense fallback={null}>
          <SessionHeader />
        </Suspense>
        {children}
      </body>
    </html>
  );
}
