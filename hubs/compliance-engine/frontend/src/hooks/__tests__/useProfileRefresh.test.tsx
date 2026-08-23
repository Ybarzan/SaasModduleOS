import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import toast from "react-hot-toast";
import { useProfileRefresh } from "../useProfileRefresh";
import { incokalkAPI } from "../../lib/api";
import { useAuthStore } from "../../stores/auth";

vi.mock("../../lib/api", () => ({
  incokalkAPI: {
    auth: { me: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const baseUser = {
  id: "u1",
  email: "test@example.com",
  firstName: "Test",
  lastName: "User",
  role: "USER" as const,
  plan: "FREE" as const,
  company: "Acme",
  companyId: "c1",
};

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe("useProfileRefresh", () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
    vi.mocked(incokalkAPI.auth.me).mockReset();
    vi.mocked(toast.success).mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("does not call the API when the user is not authenticated", async () => {
    renderHook(() => useProfileRefresh(), { wrapper });
    await new Promise((r) => setTimeout(r, 10));
    expect(incokalkAPI.auth.me).not.toHaveBeenCalled();
  });

  it("updates plan/role/company in the store when the backend reports a change", async () => {
    useAuthStore.getState().login("tok", "refresh-tok", baseUser);
    vi.mocked(incokalkAPI.auth.me).mockResolvedValue({
      data: { plan: "ENTERPRISE", role: "ADMIN", company: "Acme", company_id: "c1" },
    } as never);

    renderHook(() => useProfileRefresh(), { wrapper });

    await waitFor(() => {
      expect(useAuthStore.getState().user?.plan).toBe("ENTERPRISE");
    });
    expect(useAuthStore.getState().user?.role).toBe("ADMIN");
    expect(toast.success).toHaveBeenCalledWith(
      expect.stringContaining("ENTERPRISE"),
    );
  });

  it("leaves firstName/lastName untouched", async () => {
    useAuthStore.getState().login("tok", "refresh-tok", baseUser);
    vi.mocked(incokalkAPI.auth.me).mockResolvedValue({
      data: { plan: "PRO", role: "USER", company: "Acme", company_id: "c1" },
    } as never);

    renderHook(() => useProfileRefresh(), { wrapper });

    await waitFor(() => {
      expect(useAuthStore.getState().user?.plan).toBe("PRO");
    });
    expect(useAuthStore.getState().user?.firstName).toBe("Test");
    expect(useAuthStore.getState().user?.lastName).toBe("User");
  });

  it("does not toast or refire when the fetched profile matches the store", async () => {
    useAuthStore.getState().login("tok", "refresh-tok", baseUser);
    vi.mocked(incokalkAPI.auth.me).mockResolvedValue({
      data: { plan: "FREE", role: "USER", company: "Acme", company_id: "c1" },
    } as never);

    renderHook(() => useProfileRefresh(), { wrapper });

    await waitFor(() => {
      expect(incokalkAPI.auth.me).toHaveBeenCalled();
    });
    await new Promise((r) => setTimeout(r, 10));
    expect(toast.success).not.toHaveBeenCalled();
  });
});
