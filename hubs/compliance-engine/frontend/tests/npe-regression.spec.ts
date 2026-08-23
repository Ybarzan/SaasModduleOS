import { test, expect, request as playwrightRequest, APIRequestContext } from '@playwright/test';

// Tests de non-regression E2E pour 3 bugs NullPointerException decouverts et corriges
// en session de couverture backend (voir PROGRESS.md du 11/08/2026) :
//  1. QuoteService.parseMode() -> NPE quand le mode de transport est absent/invalide
//  2. SharedLinkController.createLink() -> NPE (500) quand le lien n'a pas de date d'expiration
// Ces tests appellent directement l'API backend reelle (pas de mock), en s'inscrivant
// comme une nouvelle societe isolee a chaque execution.
//
// Necessite un backend demarre sur http://localhost:8080 (mvn spring-boot:run).

const API_BASE = 'http://localhost:8080/api';

function randomSuffix(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

async function registerAndGetToken(api: APIRequestContext): Promise<string> {
  const suffix = randomSuffix();
  const res = await api.post(`${API_BASE}/v1/auth/register`, {
    data: {
      email: `e2e-npe-${suffix}@test.com`,
      password: 'TestPass123!',
      fullName: 'E2E NPE Regression',
      company: `E2E NPE Regression Co ${suffix}`,
    },
  });
  expect(res.status()).toBe(201);
  const body = await res.json();
  return body.token as string;
}

test.describe('Regression NPE — API backend reelle', () => {
  let api: APIRequestContext;
  let token: string;

  test.beforeAll(async () => {
    api = await playwrightRequest.newContext();
    token = await registerAndGetToken(api);
  });

  test.afterAll(async () => {
    await api.dispose();
  });

  test('POST /v1/quotes sans transportMode -> 200 avec un tarif de secours (pas de NPE)', async () => {
    const res = await api.post(`${API_BASE}/v1/quotes`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        originCountry: 'MA',
        destinationCountry: 'FR',
        weightKg: 500,
        volumeM3: 2,
        // transportMode volontairement absent
      },
    });

    expect(res.status()).toBe(200);
    const quotes = await res.json();
    expect(Array.isArray(quotes)).toBe(true);
    expect(quotes.length).toBeGreaterThan(0);
    expect(quotes[0].transportMode).toBeTruthy();
    expect(quotes[0].carrierName).toBe('IncoKalk Standard');
  });

  test('POST /v1/quotes avec un transportMode invalide -> 200 avec un tarif de secours (pas de NPE)', async () => {
    const res = await api.post(`${API_BASE}/v1/quotes`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        originCountry: 'CN',
        destinationCountry: 'FR',
        weightKg: 50,
        volumeM3: 0.5,
        transportMode: 'NOT_A_REAL_MODE',
      },
    });

    expect(res.status()).toBe(200);
    const quotes = await res.json();
    expect(quotes.length).toBeGreaterThan(0);
  });

  test('POST /v1/shared sans expiresHours -> 200 (pas de NPE), expiresAt absent de la reponse', async () => {
    const shipmentRes = await api.post(`${API_BASE}/v1/shipments`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        shipperName: 'E2E Shipper',
        shipperCity: 'Casablanca',
        shipperCountry: 'MA',
        consigneeName: 'E2E Consignee',
        consigneeCity: 'Paris',
        consigneeCountry: 'FR',
        goodsDescription: 'E2E test goods',
        weightKg: 100,
      },
    });
    expect(shipmentRes.status()).toBe(200);
    const shipment = await shipmentRes.json();

    const linkRes = await api.post(`${API_BASE}/v1/shared`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        shipmentId: shipment.id,
        // expiresHours volontairement absent -> lien sans expiration
      },
    });

    expect(linkRes.status()).toBe(200);
    const link = await linkRes.json();
    expect(link.token).toBeTruthy();
    expect(link.url).toBe(`/s/${link.token}`);
    expect(link.expiresAt).toBeUndefined();
  });

  test('GET /v1/shared/access/{token} (public, sans auth) -> retrouve le lien créé sans expiration', async () => {
    const shipmentRes = await api.post(`${API_BASE}/v1/shipments`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        shipperName: 'E2E Shipper 2',
        shipperCity: 'Tanger',
        shipperCountry: 'MA',
        consigneeName: 'E2E Consignee 2',
        consigneeCity: 'Lyon',
        consigneeCountry: 'FR',
        goodsDescription: 'E2E test goods 2',
        weightKg: 200,
      },
    });
    const shipment = await shipmentRes.json();

    const linkRes = await api.post(`${API_BASE}/v1/shared`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { shipmentId: shipment.id },
    });
    const link = await linkRes.json();

    // Endpoint public : aucun header Authorization
    const accessRes = await api.get(`${API_BASE}/v1/shared/access/${link.token}`);
    expect(accessRes.status()).toBe(200);
    const access = await accessRes.json();
    expect(access.shipment.id).toBe(shipment.id);
  });
});
