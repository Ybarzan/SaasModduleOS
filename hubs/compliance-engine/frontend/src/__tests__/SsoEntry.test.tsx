import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import SsoEntry from "../pages/SsoEntry";
import { useAuthStore } from "../stores/auth";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router-dom")>();
  return { ...actual, useNavigate: () => mockNavigate };
});

function encodeSsoPayload(payload: unknown): string {
  const json = JSON.stringify(payload);
  const base64 = btoa(unescape(encodeURIComponent(json)));
  return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function renderAtHash(hash: string) {
  window.location.hash = hash;
  return render(
    <MemoryRouter>
      <SsoEntry />
    </MemoryRouter>
  );
}

describe("SsoEntry page", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
  });

  afterEach(() => {
    window.location.hash = "";
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  it("decodes the fragment, logs the user in with the same mapping as Login.tsx, and redirects to /dashboard", async () => {
    const payload = {
      token: "jwt-abc",
      refreshToken: "refresh-abc",
      userId: "u1",
      email: "jean@incokalk.com",
      role: "ADMIN",
      plan: "PRO",
      fullName: "Jean Dupont",
    };
    renderAtHash("#" + encodeSsoPayload(payload));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/dashboard", { replace: true }));

    expect(useAuthStore.getState().token).toBe("jwt-abc");
    expect(useAuthStore.getState().refreshToken).toBe("refresh-abc");
    expect(useAuthStore.getState().user).toMatchObject({
      id: "u1",
      email: "jean@incokalk.com",
      firstName: "Jean",
      lastName: "Dupont",
      role: "ADMIN",
      plan: "PRO",
    });
  });

  it("defaults role to USER and plan to FREE when absent from the payload", async () => {
    const payload = { token: "jwt-abc", refreshToken: "refresh-abc", userId: "u1", email: "jean@incokalk.com" };
    renderAtHash("#" + encodeSsoPayload(payload));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalled());

    expect(useAuthStore.getState().user).toMatchObject({ role: "USER", plan: "FREE" });
  });

  it("shows an error and does not navigate when the fragment is invalid", async () => {
    renderAtHash("#not-valid-base64!!!");

    expect(await screen.findByText(/Lien de connexion invalide/)).toBeInTheDocument();
    expect(mockNavigate).not.toHaveBeenCalled();
    expect(useAuthStore.getState().token).toBeNull();
  });
});
