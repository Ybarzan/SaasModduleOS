import { describe, it, expect } from "vitest";
import appTsx from "../App.tsx?raw";
import { NAV_GROUPS, STANDALONE_ITEMS } from "../config/navigation";

/**
 * Guards against the class of bug fixed here: a nav item whose `requiredRole`
 * doesn't match its route's actual <ProtectedRoute> gating in App.tsx. When
 * that drifts, users either see a link that redirects them to /dashboard on
 * click (nav under-restrictive), or a link is hidden from someone who should
 * have access (nav over-restrictive).
 */
describe("navigation.ts role gating stays in sync with App.tsx routes", () => {
  const routeRequiredRole: Record<string, "MANAGER" | "ADMIN" | undefined> = {};
  const routeRe = /<Route path="(\/[a-zA-Z0-9-]+)" element=\{<ProtectedRoute([^>]*)>/g;
  let match: RegExpExecArray | null;
  while ((match = routeRe.exec(appTsx))) {
    const [, path, attrs] = match;
    if (/requireAdmin/.test(attrs)) {
      routeRequiredRole[path] = "ADMIN";
    } else {
      const roleMatch = attrs.match(/requiredRole="(\w+)"/);
      // requiredRole="USER" is the lowest tier — every authenticated role satisfies it.
      routeRequiredRole[path] = roleMatch && roleMatch[1] !== "USER" ? (roleMatch[1] as "MANAGER" | "ADMIN") : undefined;
    }
  }

  const allNavItems = [
    ...STANDALONE_ITEMS,
    ...NAV_GROUPS.flatMap((g) => g.items),
  ];

  it("has at least one role-gated route to check against (sanity check)", () => {
    expect(Object.values(routeRequiredRole).filter(Boolean).length).toBeGreaterThan(10);
  });

  it.each(allNavItems)("$to has requiredRole matching its App.tsx route", (item) => {
    const expected = routeRequiredRole[item.to];
    // Routes not found in the ProtectedRoute table (public tools, full-screen routes)
    // aren't role-gated in the nav either — nothing to check.
    if (!(item.to in routeRequiredRole)) return;
    expect(item.requiredRole).toBe(expected);
  });
});
