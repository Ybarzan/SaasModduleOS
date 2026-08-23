import { describe, it, expect } from "vitest";
import { incokalkAPI, api } from "../lib/api";

describe("incokalkAPI", () => {
  it("has auth endpoints", () => {
    expect(incokalkAPI.auth).toBeDefined();
    expect(typeof incokalkAPI.auth.login).toBe("function");
    expect(typeof incokalkAPI.auth.register).toBe("function");
    expect(typeof incokalkAPI.auth.logout).toBe("function");
    expect(typeof incokalkAPI.auth.me).toBe("function");
    expect(typeof incokalkAPI.auth.forgotPassword).toBe("function");
    expect(typeof incokalkAPI.auth.resetPassword).toBe("function");
    expect(typeof incokalkAPI.auth.verifyEmail).toBe("function");
  });

  it("has incoterms endpoints", () => {
    expect(incokalkAPI.incoterms).toBeDefined();
    expect(typeof incokalkAPI.incoterms.getAll).toBe("function");
    expect(typeof incokalkAPI.incoterms.getByCode).toBe("function");
  });

  it("has simulation endpoints", () => {
    expect(incokalkAPI.simulation).toBeDefined();
    expect(typeof incokalkAPI.simulation.calculate).toBe("function");
    expect(typeof incokalkAPI.simulation.getHistory).toBe("function");
    expect(typeof incokalkAPI.simulation.getSimulation).toBe("function");
    expect(typeof incokalkAPI.simulation.delete).toBe("function");
  });

  it("has logistics endpoints", () => {
    expect(incokalkAPI.logistics).toBeDefined();
    expect(typeof incokalkAPI.logistics.calculatePackaging).toBe("function");
    expect(typeof incokalkAPI.logistics.calculateTrucking).toBe("function");
    expect(typeof incokalkAPI.logistics.calculateCustomsDuty).toBe("function");
    expect(typeof incokalkAPI.logistics.calculateInsurance).toBe("function");
    expect(typeof incokalkAPI.logistics.optimizeRoute).toBe("function");
  });

  it("has currency endpoints", () => {
    expect(incokalkAPI.currency).toBeDefined();
    expect(typeof incokalkAPI.currency.list).toBe("function");
    expect(typeof incokalkAPI.currency.getRate).toBe("function");
    expect(typeof incokalkAPI.currency.getRates).toBe("function");
    expect(typeof incokalkAPI.currency.convert).toBe("function");
  });

  it("has user endpoints", () => {
    expect(incokalkAPI.user).toBeDefined();
    expect(typeof incokalkAPI.user.getProfile).toBe("function");
    expect(typeof incokalkAPI.user.updateProfile).toBe("function");
  });

  it("has carriers endpoints", () => {
    expect(incokalkAPI.carriers).toBeDefined();
    expect(typeof incokalkAPI.carriers.getAll).toBe("function");
    expect(typeof incokalkAPI.carriers.create).toBe("function");
    expect(typeof incokalkAPI.carriers.update).toBe("function");
    expect(typeof incokalkAPI.carriers.delete).toBe("function");
    expect(typeof incokalkAPI.carriers.toggle).toBe("function");
  });

  it("has quotes endpoints", () => {
    expect(incokalkAPI.quotes).toBeDefined();
    expect(typeof incokalkAPI.quotes.get).toBe("function");
  });

  it("has shipments endpoints", () => {
    expect(incokalkAPI.shipments).toBeDefined();
    expect(typeof incokalkAPI.shipments.getAll).toBe("function");
    expect(typeof incokalkAPI.shipments.create).toBe("function");
    expect(typeof incokalkAPI.shipments.get).toBe("function");
    expect(typeof incokalkAPI.shipments.updateStatus).toBe("function");
    expect(typeof incokalkAPI.shipments.delete).toBe("function");
  });

  it("has providers endpoints", () => {
    expect(incokalkAPI.providers).toBeDefined();
    expect(typeof incokalkAPI.providers.getAll).toBe("function");
    expect(typeof incokalkAPI.providers.create).toBe("function");
    expect(typeof incokalkAPI.providers.delete).toBe("function");
    expect(typeof incokalkAPI.providers.health).toBe("function");
    expect(typeof incokalkAPI.providers.test).toBe("function");
  });

  it("has notifications endpoints", () => {
    expect(incokalkAPI.notifications).toBeDefined();
    expect(typeof incokalkAPI.notifications.getAll).toBe("function");
    expect(typeof incokalkAPI.notifications.unreadCount).toBe("function");
    expect(typeof incokalkAPI.notifications.markRead).toBe("function");
    expect(typeof incokalkAPI.notifications.markAllRead).toBe("function");
    expect(typeof incokalkAPI.notifications.archive).toBe("function");
    expect(typeof incokalkAPI.notifications.delete).toBe("function");
  });

  it("has notificationRules endpoints", () => {
    expect(incokalkAPI.notificationRules).toBeDefined();
    expect(typeof incokalkAPI.notificationRules.getAll).toBe("function");
    expect(typeof incokalkAPI.notificationRules.create).toBe("function");
    expect(typeof incokalkAPI.notificationRules.update).toBe("function");
    expect(typeof incokalkAPI.notificationRules.delete).toBe("function");
    expect(typeof incokalkAPI.notificationRules.test).toBe("function");
  });

  it("has team endpoints", () => {
    expect(incokalkAPI.team).toBeDefined();
    expect(typeof incokalkAPI.team.list).toBe("function");
    expect(typeof incokalkAPI.team.invite).toBe("function");
    expect(typeof incokalkAPI.team.update).toBe("function");
    expect(typeof incokalkAPI.team.remove).toBe("function");
    expect(typeof incokalkAPI.team.stats).toBe("function");
  });

  it("has erp endpoints", () => {
    expect(incokalkAPI.erp).toBeDefined();
    expect(typeof incokalkAPI.erp.getAll).toBe("function");
    expect(typeof incokalkAPI.erp.create).toBe("function");
    expect(typeof incokalkAPI.erp.update).toBe("function");
    expect(typeof incokalkAPI.erp.delete).toBe("function");
    expect(typeof incokalkAPI.erp.test).toBe("function");
    expect(typeof incokalkAPI.erp.sync).toBe("function");
    expect(typeof incokalkAPI.erp.syncLogs).toBe("function");
    expect(typeof incokalkAPI.erp.health).toBe("function");
    expect(typeof incokalkAPI.erp.products).toBe("function");
    expect(typeof incokalkAPI.erp.orders).toBe("function");
    expect(typeof incokalkAPI.erp.contacts).toBe("function");
  });

  it("has audit endpoints", () => {
    expect(incokalkAPI.audit).toBeDefined();
    expect(typeof incokalkAPI.audit.getAll).toBe("function");
    expect(typeof incokalkAPI.audit.getByAction).toBe("function");
    expect(typeof incokalkAPI.audit.getByEntity).toBe("function");
    expect(typeof incokalkAPI.audit.getByUser).toBe("function");
    expect(typeof incokalkAPI.audit.getStats).toBe("function");
  });

  it("has tracking endpoints", () => {
    expect(incokalkAPI.tracking).toBeDefined();
    expect(typeof incokalkAPI.tracking.getShipment).toBe("function");
    expect(typeof incokalkAPI.tracking.getPosition).toBe("function");
    expect(typeof incokalkAPI.tracking.lookup).toBe("function");
    expect(typeof incokalkAPI.tracking.sync).toBe("function");
  });

  it("has analytics endpoints", () => {
    expect(incokalkAPI.analytics).toBeDefined();
    expect(typeof incokalkAPI.analytics.dashboard).toBe("function");
    expect(typeof incokalkAPI.analytics.shipmentsOverTime).toBe("function");
    expect(typeof incokalkAPI.analytics.shipmentsByStatus).toBe("function");
    expect(typeof incokalkAPI.analytics.costByCarrier).toBe("function");
    expect(typeof incokalkAPI.analytics.costByMode).toBe("function");
    expect(typeof incokalkAPI.analytics.topRoutes).toBe("function");
    expect(typeof incokalkAPI.analytics.incotermUsage).toBe("function");
    expect(typeof incokalkAPI.analytics.weightDistribution).toBe("function");
    expect(typeof incokalkAPI.analytics.volumeDistribution).toBe("function");
    expect(typeof incokalkAPI.analytics.costTrends).toBe("function");
    expect(typeof incokalkAPI.analytics.carrierPerformance).toBe("function");
  });

  it("has clients endpoints", () => {
    expect(incokalkAPI.clients).toBeDefined();
    expect(typeof incokalkAPI.clients.list).toBe("function");
    expect(typeof incokalkAPI.clients.create).toBe("function");
    expect(typeof incokalkAPI.clients.update).toBe("function");
    expect(typeof incokalkAPI.clients.resetPassword).toBe("function");
    expect(typeof incokalkAPI.clients.delete).toBe("function");
    expect(typeof incokalkAPI.clients.stats).toBe("function");
  });

  it("has sharedLinks endpoints", () => {
    expect(incokalkAPI.sharedLinks).toBeDefined();
    expect(typeof incokalkAPI.sharedLinks.list).toBe("function");
    expect(typeof incokalkAPI.sharedLinks.create).toBe("function");
    expect(typeof incokalkAPI.sharedLinks.linksForShipment).toBe("function");
    expect(typeof incokalkAPI.sharedLinks.revoke).toBe("function");
    expect(typeof incokalkAPI.sharedLinks.stats).toBe("function");
  });

  it("has clientAuth endpoints", () => {
    expect(incokalkAPI.clientAuth).toBeDefined();
    expect(typeof incokalkAPI.clientAuth.login).toBe("function");
    expect(typeof incokalkAPI.clientAuth.me).toBe("function");
  });

  it("has clientPortal endpoints", () => {
    expect(incokalkAPI.clientPortal).toBeDefined();
    expect(typeof incokalkAPI.clientPortal.shipments).toBe("function");
    expect(typeof incokalkAPI.clientPortal.shipmentDetail).toBe("function");
  });

  it("has documents endpoints", () => {
    expect(incokalkAPI.documents).toBeDefined();
    expect(typeof incokalkAPI.documents.exportPdf).toBe("function");
  });

  it("has trackingMap endpoints", () => {
    expect(incokalkAPI.trackingMap).toBeDefined();
    expect(typeof incokalkAPI.trackingMap.getFlights).toBe("function");
    expect(typeof incokalkAPI.trackingMap.searchVessels).toBe("function");
    expect(typeof incokalkAPI.trackingMap.getVesselPosition).toBe("function");
  });

  it("has sharedTracking endpoints", () => {
    expect(incokalkAPI.sharedTracking).toBeDefined();
    expect(typeof incokalkAPI.sharedTracking.access).toBe("function");
  });

  it("has billing endpoints", () => {
    expect(incokalkAPI.billing).toBeDefined();
    expect(typeof incokalkAPI.billing.getPlans).toBe("function");
    expect(typeof incokalkAPI.billing.status).toBe("function");
    expect(typeof incokalkAPI.billing.subscription).toBe("function");
    expect(typeof incokalkAPI.billing.checkout).toBe("function");
    expect(typeof incokalkAPI.billing.portal).toBe("function");
    expect(typeof incokalkAPI.billing.invoices).toBe("function");
  });

  it("has import endpoints", () => {
    expect(incokalkAPI.import).toBeDefined();
    expect(typeof incokalkAPI.import.carriers).toBe("function");
    expect(typeof incokalkAPI.import.preview).toBe("function");
  });

  it("has export endpoints", () => {
    expect(incokalkAPI.export).toBeDefined();
    expect(typeof incokalkAPI.export.quotesPdf).toBe("function");
    expect(typeof incokalkAPI.export.shippingLabelPdf).toBe("function");
    expect(typeof incokalkAPI.export.cmrPdf).toBe("function");
    expect(typeof incokalkAPI.export.dgdPdf).toBe("function");
    expect(typeof incokalkAPI.export.certificateOfOriginPdf).toBe("function");
    expect(typeof incokalkAPI.export.csv.shipments).toBe("function");
    expect(typeof incokalkAPI.export.csv.carriers).toBe("function");
  });
});

describe("axios api instance", () => {
  it("has baseURL set to /api by default (VITE_API_URL unset)", () => {
    expect(api.defaults.baseURL).toBe(import.meta.env.VITE_API_URL || "/api");
  });

  it("has interceptors configured", () => {
    expect(api.interceptors.request.handlers?.length).toBeGreaterThan(0);
    expect(api.interceptors.response.handlers?.length).toBeGreaterThan(0);
  });
});
