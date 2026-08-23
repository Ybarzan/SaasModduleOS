import { describe, it, expect, beforeEach } from "vitest";
import { useAuthStore, type UserRole } from "../stores/auth";

const baseUser = {
  id: "u1",
  email: "test@example.com",
  firstName: "Test",
  lastName: "User",
};

describe("authStore", () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null, refreshToken: null, user: null });
  });

  describe("login", () => {
    it("sets token and user", () => {
      useAuthStore.getState().login("tok-123", "refresh-tok", { ...baseUser, role: "ADMIN" });
      const { token, user } = useAuthStore.getState();
      expect(token).toBe("tok-123");
      expect(user?.email).toBe("test@example.com");
      expect(user?.role).toBe("ADMIN");
    });
  });

  describe("logout", () => {
    it("clears token and user", () => {
      useAuthStore.getState().login("tok-123", "refresh-tok", { ...baseUser, role: "ADMIN" });
      useAuthStore.getState().logout();
      expect(useAuthStore.getState().token).toBeNull();
      expect(useAuthStore.getState().user).toBeNull();
    });
  });

  describe("setUser", () => {
    it("updates user without changing token", () => {
      useAuthStore.getState().login("tok-123", "refresh-tok", { ...baseUser, role: "USER" });
      useAuthStore.getState().setUser({ ...baseUser, role: "ADMIN", firstName: "Admin" });
      expect(useAuthStore.getState().token).toBe("tok-123");
      expect(useAuthStore.getState().user?.firstName).toBe("Admin");
      expect(useAuthStore.getState().user?.role).toBe("ADMIN");
    });
  });

  describe("isAuthenticated", () => {
    it("returns false when not logged in", () => {
      expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    });

    it("returns true when logged in", () => {
      useAuthStore.getState().login("tok-123", "refresh-tok", { ...baseUser, role: "USER" });
      expect(useAuthStore.getState().isAuthenticated()).toBe(true);
    });

    it("returns false with only token set", () => {
      useAuthStore.setState({ token: "tok-123", user: null });
      expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    });

    it("returns false with only user set", () => {
      useAuthStore.setState({ token: null, user: { ...baseUser, role: "ADMIN" } });
      expect(useAuthStore.getState().isAuthenticated()).toBe(false);
    });
  });

  describe("isAdmin", () => {
    it("returns true for OWNER", () => {
      useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role: "OWNER" });
      expect(useAuthStore.getState().isAdmin()).toBe(true);
    });

    it("returns true for ADMIN", () => {
      useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role: "ADMIN" });
      expect(useAuthStore.getState().isAdmin()).toBe(true);
    });

    it("returns false for non-admin", () => {
      useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role: "MANAGER" });
      expect(useAuthStore.getState().isAdmin()).toBe(false);
    });
  });

  describe("hasRole", () => {
    it("returns true for exact role match", () => {
      useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role: "USER" });
      expect(useAuthStore.getState().hasRole("USER")).toBe(true);
    });

    it("returns false for different role", () => {
      useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role: "USER" });
      expect(useAuthStore.getState().hasRole("ADMIN")).toBe(false);
    });
  });

  describe("hasMinimumRole", () => {
    const roles: UserRole[] = ["OWNER", "ADMIN", "MANAGER", "USER"];

    it("OWNER satisfies all roles", () => {
      useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role: "OWNER" });
      for (const role of roles) {
        expect(useAuthStore.getState().hasMinimumRole(role)).toBe(true);
      }
    });

    it("ADMIN satisfies ADMIN, MANAGER, USER", () => {
      useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role: "ADMIN" });
      expect(useAuthStore.getState().hasMinimumRole("OWNER")).toBe(false);
      expect(useAuthStore.getState().hasMinimumRole("ADMIN")).toBe(true);
      expect(useAuthStore.getState().hasMinimumRole("MANAGER")).toBe(true);
      expect(useAuthStore.getState().hasMinimumRole("USER")).toBe(true);
    });

    it("MANAGER satisfies MANAGER, USER", () => {
      useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role: "MANAGER" });
      expect(useAuthStore.getState().hasMinimumRole("OWNER")).toBe(false);
      expect(useAuthStore.getState().hasMinimumRole("ADMIN")).toBe(false);
      expect(useAuthStore.getState().hasMinimumRole("MANAGER")).toBe(true);
      expect(useAuthStore.getState().hasMinimumRole("USER")).toBe(true);
    });

    it("USER only satisfies USER", () => {
      useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role: "USER" });
      expect(useAuthStore.getState().hasMinimumRole("OWNER")).toBe(false);
      expect(useAuthStore.getState().hasMinimumRole("ADMIN")).toBe(false);
      expect(useAuthStore.getState().hasMinimumRole("MANAGER")).toBe(false);
      expect(useAuthStore.getState().hasMinimumRole("USER")).toBe(true);
    });

    it("returns false when not logged in", () => {
      expect(useAuthStore.getState().hasMinimumRole("USER")).toBe(false);
    });
  });

  describe("canEdit", () => {
    const editableRoles: UserRole[] = ["OWNER", "ADMIN", "MANAGER", "USER"];

    it.each(editableRoles)("allows %s to edit", (role) => {
      useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role });
      expect(useAuthStore.getState().canEdit()).toBe(true);
    });
  });

  describe("canManageTeam", () => {
    it("OWNER can manage team", () => {
      useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role: "OWNER" });
      expect(useAuthStore.getState().canManageTeam()).toBe(true);
    });

    it("ADMIN can manage team", () => {
      useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role: "ADMIN" });
      expect(useAuthStore.getState().canManageTeam()).toBe(true);
    });

    it("MANAGER can manage team", () => {
      useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role: "MANAGER" });
      expect(useAuthStore.getState().canManageTeam()).toBe(true);
    });

    it("USER cannot manage team", () => {
      useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role: "USER" });
      expect(useAuthStore.getState().canManageTeam()).toBe(false);
    });
  });

  describe("canManageShipments", () => {
    it.each(["OWNER", "ADMIN", "MANAGER", "USER"] as UserRole[])(
      "%s can manage shipments",
      (role) => {
        useAuthStore.getState().login("tok", "refresh-tok", { ...baseUser, role });
        expect(useAuthStore.getState().canManageShipments()).toBe(true);
      },
    );
  });
});
