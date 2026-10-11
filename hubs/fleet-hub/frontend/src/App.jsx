import { Suspense, lazy } from 'react'
import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import Layout from './components/Layout'
import Login from './pages/Login'

const Register = lazy(() => import('./pages/Register'))
const AcceptInvitation = lazy(() => import('./pages/AcceptInvitation'))
const ForgotPassword = lazy(() => import('./pages/ForgotPassword'))
const ResetPassword = lazy(() => import('./pages/ResetPassword'))
const Legal = lazy(() => import('./pages/Legal'))
const Admin = lazy(() => import('./pages/Admin'))
const Dashboard = lazy(() => import('./pages/Dashboard'))
const Drivers = lazy(() => import('./pages/Drivers'))
const DriverDetail = lazy(() => import('./pages/DriverDetail'))
const Trucks = lazy(() => import('./pages/Trucks'))
const TruckDetail = lazy(() => import('./pages/TruckDetail'))
const MapPage = lazy(() => import('./pages/MapPage'))
const Tachographie = lazy(() => import('./pages/Tachographie'))
const Pointage = lazy(() => import('./pages/Pointage'))
const DataEntry = lazy(() => import('./pages/DataEntry'))
const Billing = lazy(() => import('./pages/Billing'))
const Users = lazy(() => import('./pages/Users'))
const Notifications = lazy(() => import('./pages/Notifications'))
const Integrations = lazy(() => import('./pages/Integrations'))
const Marketplace = lazy(() => import('./pages/Marketplace'))
const DataImport = lazy(() => import('./pages/DataImport'))
const Privacy = lazy(() => import('./pages/Privacy'))
const Settings = lazy(() => import('./pages/Settings'))
const PointagePortal = lazy(() => import('./pages/pointage/PointagePortal'))

// Transport (expéditions, transporteurs, tarifs, suivi)
const Shipments = lazy(() => import('./pages/Shipments'))
const Carriers = lazy(() => import('./pages/Carriers'))
const CarrierBookings = lazy(() => import('./pages/CarrierBookings'))
const CarrierInvoicing = lazy(() => import('./pages/CarrierInvoicing'))
const ShippingRates = lazy(() => import('./pages/ShippingRates'))
const RateComparison = lazy(() => import('./pages/RateComparison'))
const Quotes = lazy(() => import('./pages/Quotes'))
const Providers = lazy(() => import('./pages/Providers'))
const ProviderHealth = lazy(() => import('./pages/ProviderHealth'))
const TrackingLookup = lazy(() => import('./pages/TrackingLookup'))
const ShipTracker = lazy(() => import('./pages/ShipTracker'))
const FlightRadar = lazy(() => import('./pages/FlightRadar'))
const Logistics = lazy(() => import('./pages/Logistics'))
const RouteOptimizer = lazy(() => import('./pages/RouteOptimizer'))
const OptimizationDashboard = lazy(() => import('./pages/OptimizationDashboard'))
const EtaPredictions = lazy(() => import('./pages/EtaPredictions'))
const GroupagePage = lazy(() => import('./pages/GroupagePage'))
const Clients = lazy(() => import('./pages/Clients'))
const SharedLinks = lazy(() => import('./pages/SharedLinks'))
const BrandingPortal = lazy(() => import('./pages/BrandingPortal'))

// Portail client (suivi d'expédition pour les clients des expéditeurs) — hors
// layout back-office, hors AuthContext (voir pages/client/).
const ClientLogin = lazy(() => import('./pages/client/ClientLogin'))
const ClientDashboard = lazy(() => import('./pages/client/ClientDashboard'))
const ClientShipmentDetail = lazy(() => import('./pages/client/ClientShipmentDetail'))
const SharedTracking = lazy(() => import('./pages/client/SharedTracking'))

function PageFallback() {
  return (
    <div className="page-loading" role="status" aria-live="polite">
      <span className="spinner" />
    </div>
  )
}

function RequireAuth({ children }) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" state={{ from: location }} replace />
  // Le portail chauffeur est un espace à part : jamais le back-office.
  if (user.role === 'CHAUFFEUR') return <Navigate to="/pointage" replace />
  return children
}

function RequireRole({ role, children }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (user.role !== role) return <Navigate to="/" replace />
  return children
}

function RequireTenantAdmin({ children }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (user.role !== 'ADMIN') return <Navigate to="/" replace />
  return children
}

function RequireClientAuth({ children }) {
  let hasClientSession = false
  try {
    hasClientSession =
      !!localStorage.getItem('fh_client_token') && !!localStorage.getItem('fh_client_user')
  } catch {
    /* localStorage indisponible (navigation privée, etc.) */
  }
  if (!hasClientSession) return <Navigate to="/client/login" replace />
  return children
}

const FROZEN_ALLOWED = ['/billing', '/rgpd']

function RequireSubscription({ children }) {
  const { user } = useAuth()
  const location = useLocation()
  if (user && user.subscriptionActive === false && !FROZEN_ALLOWED.includes(location.pathname)) {
    return <Navigate to="/billing" replace />
  }
  return children
}

export default function App() {
  return (
    <Suspense fallback={<PageFallback />}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/accept-invitation" element={<AcceptInvitation />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route path="/legal/:key" element={<Legal />} />
        <Route path="/pointage" element={<PointagePortal />} />
        <Route path="/pointage/:accessCode" element={<PointagePortal />} />

        {/* Portail client (suivi d'expédition) — public / authentification client dédiée */}
        <Route path="/client/login" element={<ClientLogin />} />
        <Route
          path="/client/dashboard"
          element={
            <RequireClientAuth>
              <ClientDashboard />
            </RequireClientAuth>
          }
        />
        <Route
          path="/client/shipments/:id"
          element={
            <RequireClientAuth>
              <ClientShipmentDetail />
            </RequireClientAuth>
          }
        />
        <Route path="/s/:token" element={<SharedTracking />} />

        <Route
          element={
            <RequireAuth>
              <RequireSubscription>
                <Layout />
              </RequireSubscription>
            </RequireAuth>
          }
        >
          <Route path="/" element={<Dashboard />} />
          <Route path="/drivers" element={<Drivers />} />
          <Route path="/drivers/:assignmentId" element={<DriverDetail />} />
          <Route path="/trucks" element={<Trucks />} />
          <Route path="/trucks/:truckId" element={<TruckDetail />} />
          <Route path="/map" element={<MapPage />} />
          <Route path="/tachographie" element={<Tachographie />} />
          <Route path="/pointage-suivi" element={<Pointage />} />
          <Route path="/data" element={<DataEntry />} />
          <Route path="/import" element={<DataImport />} />
          <Route path="/billing" element={<Billing />} />
          <Route path="/notifications" element={<Notifications />} />
          <Route
            path="/users"
            element={
              <RequireTenantAdmin>
                <Users />
              </RequireTenantAdmin>
            }
          />
          <Route
            path="/integrations"
            element={
              <RequireTenantAdmin>
                <Integrations />
              </RequireTenantAdmin>
            }
          />
          <Route
            path="/marketplace"
            element={
              <RequireTenantAdmin>
                <Marketplace />
              </RequireTenantAdmin>
            }
          />
          <Route
            path="/rgpd"
            element={
              <RequireTenantAdmin>
                <Privacy />
              </RequireTenantAdmin>
            }
          />
          <Route
            path="/admin"
            element={
              <RequireRole role="SAAS_ADMIN">
                <Admin />
              </RequireRole>
            }
          />
          <Route path="/settings" element={<Settings />} />

          {/* Transport : expéditions, transporteurs, tarifs, suivi */}
          <Route path="/shipments" element={<Shipments />} />
          <Route path="/carriers" element={<Carriers />} />
          <Route path="/carrier-bookings" element={<CarrierBookings />} />
          <Route path="/carrier-invoices" element={<CarrierInvoicing />} />
          <Route path="/shipping-rates" element={<ShippingRates />} />
          <Route path="/rate-comparison" element={<RateComparison />} />
          <Route path="/quotes" element={<Quotes />} />
          <Route path="/providers" element={<Providers />} />
          <Route path="/providers/health" element={<ProviderHealth />} />
          <Route path="/tracking" element={<TrackingLookup />} />
          <Route path="/ship-tracker" element={<ShipTracker />} />
          <Route path="/flight-radar" element={<FlightRadar />} />
          <Route path="/logistics" element={<Logistics />} />
          <Route path="/route-optimizer" element={<RouteOptimizer />} />
          <Route path="/optimization" element={<OptimizationDashboard />} />
          <Route path="/eta-predictions" element={<EtaPredictions />} />
          <Route path="/groupage" element={<GroupagePage />} />
          <Route
            path="/clients"
            element={
              <RequireTenantAdmin>
                <Clients />
              </RequireTenantAdmin>
            }
          />
          <Route
            path="/shared-links"
            element={
              <RequireTenantAdmin>
                <SharedLinks />
              </RequireTenantAdmin>
            }
          />
          <Route
            path="/branding"
            element={
              <RequireTenantAdmin>
                <BrandingPortal />
              </RequireTenantAdmin>
            }
          />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
