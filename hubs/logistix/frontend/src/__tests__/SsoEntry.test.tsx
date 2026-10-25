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

describe("SsoEntry", () => {
  beforeEach(() => {
    mockNavigate.mockClear();
    useAuthStore.setState({ token: null, user: null });
  });

  afterEach(() => {
    window.location.hash = "";
  });

  it("decodes the fragment, logs the user in, and redirects to /warehouses", async () => {
    const payload = {
      token: "jwt-abc",
      email: "ops@acme.fr",
      displayName: "Ops Acme",
      role: "ADMIN",
      companyId: "company-1",
      companyName: "Acme",
    };
    renderAtHash("#" + encodeSsoPayload(payload));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/warehouses", { replace: true }));

    expect(useAuthStore.getState().token).toBe("jwt-abc");
    expect(useAuthStore.getState().user).toMatchObject({
      id: "ops@acme.fr",
      email: "ops@acme.fr",
      firstName: "Ops",
      lastName: "Acme",
      role: "ADMIN",
      companyId: "company-1",
    });
  });

  it("shows an error and does not navigate when the fragment is invalid", async () => {
    renderAtHash("#not-valid-base64!!!");

    expect(await screen.findByText(/Lien de connexion invalide/)).toBeInTheDocument();
    expect(mockNavigate).not.toHaveBeenCalled();
    expect(useAuthStore.getState().token).toBeNull();
  });
});
