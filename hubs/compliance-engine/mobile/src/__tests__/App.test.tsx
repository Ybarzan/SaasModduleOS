import { describe, it, expect, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import App from "../App";
import { useAuthStore } from "../stores/auth";

describe("App smoke test", () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null, hasHydrated: true });
  });

  it("mounts without crashing and shows the login screen when unauthenticated", async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByText("IncoKalk")).toBeInTheDocument();
    });
    expect(screen.getByPlaceholderText("Email")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Mot de passe")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Se connecter" })).toBeInTheDocument();
  });

  it("redirects to /dashboard content once authenticated", async () => {
    useAuthStore.setState({
      token: "tok",
      refreshToken: "r",
      user: { id: "u1", email: "a@b.com", firstName: "A", lastName: "B" },
      hasHydrated: true,
    });

    render(<App />);

    await waitFor(() => {
      expect(screen.queryByPlaceholderText("Email")).not.toBeInTheDocument();
    });
  });
});
