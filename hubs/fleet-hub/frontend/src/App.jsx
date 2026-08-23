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
const DataEntry = lazy(() => import('./pages/DataEntry'))
const Billing = lazy(() => import('./pages/Billing'))
const Users = lazy(() => import('./pages/Users'))
const Notifications = lazy(() => import('./pages/Notifications'))
const Integrations = lazy(() => import('./pages/Integrations'))
const DataImport = lazy(() => import('./pages/DataImport'))
const Privacy = lazy(() => import('./pages/Privacy'))
const Settings = lazy(() => import('./pages/Settings'))

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
        <Route element={<RequireAuth><RequireSubscription><Layout /></RequireSubscription></RequireAuth>}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/drivers" element={<Drivers />} />
          <Route path="/drivers/:assignmentId" element={<DriverDetail />} />
          <Route path="/trucks" element={<Trucks />} />
          <Route path="/trucks/:truckId" element={<TruckDetail />} />
          <Route path="/map" element={<MapPage />} />
          <Route path="/tachographie" element={<Tachographie />} />
          <Route path="/data" element={<DataEntry />} />
          <Route path="/import" element={<DataImport />} />
          <Route path="/billing" element={<Billing />} />
          <Route path="/notifications" element={<Notifications />} />
          <Route
            path="/users"
            element={<RequireTenantAdmin><Users /></RequireTenantAdmin>}
          />
          <Route
            path="/integrations"
            element={<RequireTenantAdmin><Integrations /></RequireTenantAdmin>}
          />
          <Route
            path="/rgpd"
            element={<RequireTenantAdmin><Privacy /></RequireTenantAdmin>}
          />
          <Route
            path="/admin"
            element={<RequireRole role="SAAS_ADMIN"><Admin /></RequireRole>}
          />
          <Route path="/settings" element={<Settings />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
