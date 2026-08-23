import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Billing from "../pages/Billing";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: {
    billing: { status: vi.fn(), invoices: vi.fn(), portal: vi.fn() },
    companies: { me: vi.fn() },
  },
}));

vi.mock("react-hot-toast", () => ({
  default: { success: vi.fn(), error: vi.fn() },
}));

const subscription = {
  id: "s1",
  plan: "PRO",
  status: "ACTIVE",
  billingCycle: "MONTHLY",
  cancelAtPeriodEnd: false,
  currentPeriodStart: "2026-08-01T00:00:00Z",
  currentPeriodEnd: "2026-09-01T00:00:00Z",
  canceledAt: "",
  createdAt: "2026-01-01T00:00:00Z",
};

const invoices = [
  {
    id: "i1",
    stripeInvoiceId: "in_123",
    amountCents: 14900,
    currency: "EUR",
    status: "PAID",
    description: "Abonnement Pro",
    invoiceUrl: "",
    invoicePdfUrl: "https://example.com/inv.pdf",
    periodStart: "",
    periodEnd: "",
    paidAt: "2026-08-01T00:00:00Z",
    createdAt: "2026-08-01T00:00:00Z",
  },
];

function mockDefaults() {
  vi.mocked(incokalkAPI.billing.status).mockResolvedValue({
    data: { stripeConfigured: true, subscription },
  } as never);
  vi.mocked(incokalkAPI.billing.invoices).mockResolvedValue({ data: invoices } as never);
  vi.mocked(incokalkAPI.companies.me).mockResolvedValue({ data: { referralCode: "ABCD1234" } } as never);
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Billing />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Billing page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDefaults();
  });

  it("renders the current plan and status", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Pro")).toBeInTheDocument();
    });
    expect(screen.getByText("Actif")).toBeInTheDocument();
  });

  it("renders the invoice history", async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Abonnement Pro")).toBeInTheDocument();
    });
  });

  it("shows the empty state when there are no invoices", async () => {
    vi.mocked(incokalkAPI.billing.invoices).mockResolvedValue({ data: [] } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Aucune facture pour le moment")).toBeInTheDocument();
    });
  });

  it("shows a warning banner when Stripe is not configured", async () => {
    vi.mocked(incokalkAPI.billing.status).mockResolvedValue({
      data: { stripeConfigured: false, subscription },
    } as never);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Stripe non configuré")).toBeInTheDocument();
    });
  });

  it("opens the billing portal", async () => {
    vi.mocked(incokalkAPI.billing.portal).mockResolvedValue({ data: { url: "https://billing.stripe.com/session" } } as never);
    renderPage();
    await waitFor(() => screen.getByText("Pro"));

    fireEvent.click(screen.getByRole("button", { name: /Gérer l'abonnement/ }));
    await waitFor(() => {
      expect(incokalkAPI.billing.portal).toHaveBeenCalled();
    });
  });

  it("shows the referral link and copies it to the clipboard", async () => {
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } });
    renderPage();

    await waitFor(() => {
      expect(screen.getByDisplayValue(/\/register\?ref=ABCD1234$/)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /Copier le lien/ }));
    await waitFor(() => {
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith(expect.stringContaining("/register?ref=ABCD1234"));
    });
    expect(await screen.findByRole("button", { name: /Copié/ })).toBeInTheDocument();
  });
});
