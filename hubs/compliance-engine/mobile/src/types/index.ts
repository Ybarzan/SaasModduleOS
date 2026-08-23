export interface DashboardSummary {
  [key: string]: unknown;
}

export interface RecentShipment {
  id: string;
  order_number: string;
  status: string;
  origin: { country?: string; city?: string };
  destination: { country?: string; city?: string };
  goods_description?: string;
  weight_kg?: number;
  incoterm?: string;
  total_cost?: number;
  currency?: string;
  carrier_name?: string;
  transport_mode?: string;
  created_at?: string;
  estimated_delivery?: string;
}

export interface ShipmentDetail {
  id: string;
  orderNumber: string;
  status: 'DRAFT' | 'QUOTED' | 'BOOKED' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED';
  shipperName?: string;
  shipperAddress?: string;
  shipperCity?: string;
  shipperCountry?: string;
  shipperPostalCode?: string;
  consigneeName?: string;
  consigneeAddress?: string;
  consigneeCity?: string;
  consigneeCountry?: string;
  consigneePostalCode?: string;
  goodsDescription?: string;
  goodsValue?: number;
  currency?: string;
  weightKg?: number;
  volumeM3?: number;
  packagesCount?: number;
  hsCode?: string;
  incotermCode?: string;
  dangerous?: boolean;
  countryOfOrigin?: string;
  containerType?: string;
  carrierName?: string;
  transportMode?: string;
  customsStatus?: string;
  customsDeclarationNumber?: string;
  dutyAmount?: number;
  vatAmount?: number;
  landedCost?: number;
  quotedCost?: number;
  finalCost?: number;
  costCurrency?: string;
  requestedPickupDate?: string;
  estimatedDeliveryDate?: string;
  actualDeliveryDate?: string;
  bookedAt?: string;
  shippedAt?: string;
  createdAt?: string;
  trackingEvents?: TrackingEvent[];
}

export interface TrackingEvent {
  id: string;
  status: string;
  location?: string;
  description?: string;
  eventTime: string;
  source?: string;
}

export interface ShipmentItemDTO {
  id: string;
  sku?: string;
  name?: string;
  description?: string;
  hsCode?: string;
  originCountry?: string;
  quantity: number;
  unit: string;
  unitPrice: number;
}

export const TRACKING_STATUS_LABELS: Record<string, string> = {
  CREATED: 'Créé',
  DEPARTED: 'Parti',
  IN_TRANSIT: 'En transit',
  ARRIVED: 'Arrivé',
  DELIVERED: 'Livré',
  CUSTOMS_CLEARANCE: 'Dédouanement',
  DELAYED: 'Retardé',
};

export const CUSTOMS_STATUS_LABELS: Record<string, string> = {
  NONE: 'Aucune',
  PENDING: 'En attente',
  IN_PROGRESS: 'En cours',
  CLEARED: 'Dédouané',
  HELD: 'Bloqué',
};

export interface MobileNotification {
  id: string;
  title: string;
  body: string;
  type: string;
  referenceId?: string;
  isRead: boolean;
  sentAt: string;
}

export interface QuickQuoteResult {
  origin: string;
  destination: string;
  weight_kg: number;
  incoterm: string;
  currency: string;
  estimated_total: number;
  breakdown: {
    base_freight: number;
    fuel_surcharge: number;
    security_surcharge: number;
    handling_fee: number;
    documentation_fee: number;
  };
}

export const STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Brouillon',
  QUOTED: 'Devisé',
  BOOKED: 'Réservé',
  IN_TRANSIT: 'En transit',
  DELIVERED: 'Livré',
  CANCELLED: 'Annulé',
};

export interface ApprovalRequest {
  id: string;
  entityType: string;
  entityId?: string;
  entityReference?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  requestedByUserId?: string;
  requestedAt: string;
  amount?: number;
  currency?: string;
  currentStep?: number;
  totalSteps?: number;
  notes?: string;
}

export const APPROVAL_ENTITY_LABELS: Record<string, string> = {
  QUOTE: 'Devis',
  CARRIER_INVOICE: 'Facture transporteur',
  PURCHASE_ORDER: 'Bon de commande',
  EXPENSE_REPORT: 'Note de frais',
  CUSTOM: 'Autre',
};

export type ParsedDocumentType = 'COMMERCIAL_INVOICE' | 'BILL_OF_LADING' | 'CERTIFICATE_OF_ORIGIN' | 'PACKING_LIST';

export const DOCUMENT_TYPE_LABELS: Record<ParsedDocumentType, string> = {
  COMMERCIAL_INVOICE: 'Facture commerciale',
  BILL_OF_LADING: 'Connaissement (B/L)',
  CERTIFICATE_OF_ORIGIN: "Certificat d'origine",
  PACKING_LIST: 'Liste de colisage',
};

export interface ParsedDocument {
  id: string;
  documentType: ParsedDocumentType;
  originalFilename?: string;
  confidence?: number;
  parsedData?: Record<string, unknown>;
  status: 'PARSED' | 'VERIFIED' | 'REJECTED';
  createdAt?: string;
}

export const STATUS_TRANSITIONS: Record<string, string[]> = {
  DRAFT: ['QUOTED', 'CANCELLED'],
  QUOTED: ['BOOKED', 'CANCELLED'],
  BOOKED: ['IN_TRANSIT', 'CANCELLED'],
  IN_TRANSIT: ['DELIVERED'],
  DELIVERED: [],
  CANCELLED: [],
};
