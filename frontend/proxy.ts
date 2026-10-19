import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

// Cheap gate: no session cookie -> sign-in. The token itself is validated by the backend.
export function proxy(request: NextRequest) {
  if (!request.cookies.has("vc_session")) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!login|_next/static|_next/image|favicon.ico|.*\\..*).*)"],
};
