import { describe, it, expect, beforeEach } from "vitest";
import { useClientAuthStore } from "../stores/clientAuth";

const mockClient = {
  id: "c1",
  email: "client@test.com",
  fullName: "Client Test",
  companyId: "comp1",
};

describe("clientAuthStore", () => {
  beforeEach(() => {
    useClientAuthStore.setState({ token: null, client: null });
  });

  describe("login", () => {
    it("sets token and client", () => {
      useClientAuthStore.getState().login("client-tok", mockClient);
      const { token, client } = useClientAuthStore.getState();
      expect(token).toBe("client-tok");
      expect(client?.email).toBe("client@test.com");
      expect(client?.fullName).toBe("Client Test");
    });
  });

  describe("logout", () => {
    it("clears token and client", () => {
      useClientAuthStore.getState().login("client-tok", mockClient);
      useClientAuthStore.getState().logout();
      expect(useClientAuthStore.getState().token).toBeNull();
      expect(useClientAuthStore.getState().client).toBeNull();
    });
  });

  describe("isAuthenticated", () => {
    it("returns false when not logged in", () => {
      expect(useClientAuthStore.getState().isAuthenticated()).toBe(false);
    });

    it("returns true when logged in", () => {
      useClientAuthStore.getState().login("client-tok", mockClient);
      expect(useClientAuthStore.getState().isAuthenticated()).toBe(true);
    });

    it("returns false with only token set", () => {
      useClientAuthStore.setState({ token: "tok", client: null });
      expect(useClientAuthStore.getState().isAuthenticated()).toBe(false);
    });

    it("returns false with only client set", () => {
      useClientAuthStore.setState({ token: null, client: mockClient });
      expect(useClientAuthStore.getState().isAuthenticated()).toBe(false);
    });
  });
});
