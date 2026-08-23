import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import SharedTracking from "../pages/SharedTracking";
import { incokalkAPI } from "../lib/api";

vi.mock("../lib/api", () => ({
  incokalkAPI: { sharedTracking: { access: vi.fn() } },
}));

const trackingData = {
  shipment: {
    id: "s1",
    status: "IN_TRANSIT",
    shipperCity: "Shanghai",
    shipperCountry: "CN",
    consigneeCity: "Paris",
    consigneeCountry: "FR",
    carrierName: "DHL",
  },
  trackingEvents: [
    { id: "e1", status: "DEPARTED", location: "Shanghai", description: "Parti du port", eventTime: "2026-08-01T00:00:00Z" },
  ],
  companyName: "Atlas Import Export",
  companyLogo: null,
  label: "Suivi commande #4521",
};

function renderPage(token = "tok-abc") {
  return render(
    <MemoryRouter initialEntries={[`/track/${token}`]}>
      <Routes>
        <Route path="/track/:token" element={<SharedTracking />} />
      </Routes>
    </MemoryRouter>
  );
}

describe("SharedTracking page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows the branded shipment status and route once loaded", async () => {
    vi.mocked(incokalkAPI.sharedTracking.access).mockResolvedValue({ data: trackingData } as never);
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Atlas Import Export")).toBeInTheDocument();
    });
    expect(incokalkAPI.sharedTracking.access).toHaveBeenCalledWith("tok-abc");
    expect(screen.getAllByText("Shanghai").length).toBeGreaterThan(0);
    expect(screen.getByText("Paris")).toBeInTheDocument();
    expect(screen.getByText("Suivi commande #4521")).toBeInTheDocument();
  });

  it("shows the tracking timeline", async () => {
    vi.mocked(incokalkAPI.sharedTracking.access).mockResolvedValue({ data: trackingData } as never);
    renderPage();
    await waitFor(() => screen.getByText("Atlas Import Export"));
    expect(screen.getByText("Parti du port")).toBeInTheDocument();
  });

  it("shows an invalid-link message when the token doesn't resolve", async () => {
    vi.mocked(incokalkAPI.sharedTracking.access).mockRejectedValue({
      response: { data: { message: "Lien expiré" } },
    });
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("Lien invalide")).toBeInTheDocument();
    });
    expect(screen.getByText("Lien expiré")).toBeInTheDocument();
  });
});
