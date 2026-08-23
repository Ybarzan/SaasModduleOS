# IncoKalk Frontend

> Interface 100% française de la plateforme SaaS IncoKalk — calculateur et outil d'opérations logistiques (Incoterms 2020, douanes françaises, tracking, e-commerce, ERP, finance, CSRD).

---

## Stack

| Composant | Technologie |
|---|---|
| Framework | React 19 |
| Langage | TypeScript 5.9 |
| Build | Vite 5.4 |
| Styling | Tailwind CSS 3.4 |
| Routing | react-router-dom 7.13 (lazy loading) |
| State global | Zustand 4.5 (auth) |
| Données serveur | @tanstack/react-query 5.20 |
| HTTP client | Axios (`frontend/src/lib/api.ts`) |
| Graphiques | Recharts 3.8 |
| Cartes | Leaflet 1.9 + react-leaflet 5.0 |
| PDF | jsPDF 4.2 |
| Tests unitaires | Vitest 4.1 + @testing-library/react |
| Tests E2E | Playwright 1.49 |
| PWA | Manifest + Service Worker |

---

## Structure du projet

```
frontend/
├── src/
│   ├── App.tsx                  # Routing lazy + guards par rôle
│   ├── main.tsx                 # Point d'entrée React
│   ├── components/              # Composants partagés (Navbar, Footer, PWAInstallPrompt...)
│   ├── hooks/                   # Hooks personnalisés (useBarcodeScanner, useAuth...)
│   ├── lib/
│   │   ├── api.ts               # Client API centralisé (54 groupes d'endpoints)
│   │   ├── constants.ts         # Constantes (INCOTERMS, COUNTRIES, STATUS_CONFIG...)
│   │   └── offlineQueue.ts      # File d'attente hors ligne pour scans PWA
│   ├── pages/                   # 73 pages (lazy-loaded)
│   │   ├── warehouse/           # Pages entrepôts & réception
│   │   ├── shipment/            # Pages expéditions & tracking
│   │   ├── compliance/          # Pages douanes & conformité
│   │   ├── financial/           # Pages finance & facturation
│   │   └── ...
│   ├── stores/                  # Stores Zustand (auth, clientAuth)
│   ├── types/                   # Types TypeScript globaux
│   └── __tests__/               # Tests Vitest
├── Dockerfile
├── package.json
├── playwright.config.ts
└── vitest.config.ts
```

---

## Démarrage rapide

### Prérequis

- Node.js 20+
- npm 10+

### Développement local

```bash
cd frontend

# Installer les dépendances
npm install

# Lancer le serveur de développement
npm run dev
```

L'interface est disponible sur `http://localhost:5173`.

### Variables d'environnement

Créer un fichier `.env` à la racine du dossier `frontend/` :

```env
VITE_API_URL=http://localhost:8080/api
```

Par défaut, l'API lit `import.meta.env.VITE_API_URL` (fallback vers `/api`, proxy Vite/nginx).

---

## Routing

Le routing est lazy-loaded avec `react-router-dom` v7. Chaque page est protégée par un `ProtectedRoute` qui vérifie l'authentification et le rôle minimum requis.

### Pages principales

| Route | Rôle minimum | Description |
|---|---|---|
| `/` | PUBLIC | Page d'accueil |
| `/login` | PUBLIC | Connexion |
| `/dashboard` | USER | Tableau de bord |
| `/shipments` | USER | Expéditions |
| `/warehouses` | USER | Entrepôts |
| `/inventory-items` | USER | Catalogue articles |
| `/inventory` | USER | Stock |
| `/receivings` | USER | Réception marchandises |
| `/scan-receiving` | USER | Scanner réception (PWA) |
| `/logistics` | USER | Dashboard logistique |
| `/customs` | MANAGER | Simulateur douane |
| `/eta-predictions` | MANAGER | Prédictions ETA |
| `/approvals` | MANAGER | Workflows approbation |
| `/carbon` | MANAGER | Dashboard carbone |
| `/ecommerce` | MANAGER | Intégrations e-commerce |
| `/branches` | ADMIN | Multi-branche |
| `/csrd` | ADMIN | Reporting CSRD |
| `/billing` | ADMIN | Facturation |
| `/roles` | ADMIN | Gestion des rôles |
| `/api-keys` | ADMIN | Clés API |

### Guards par rôle

```tsx
<ProtectedRoute requiredRole="USER">
  <Shipments />
</ProtectedRoute>

<ProtectedRoute requireAdmin>
  <AuditLog />
</ProtectedRoute>
```

---

## API Client

Le client API est centralisé dans `frontend/src/lib/api.ts` :

- **Base URL** : `import.meta.env.VITE_API_URL` (fallback `/api`)
- **Refresh token** : single-flight mechanism pour éviter les appels concurrents
- **Interceptors** : ajout automatique du JWT et du `X-Tenant-ID`
- **Groupes d'endpoints** : 54 groupes (shipments, receivings, inventory, warehouses, compliance, financial, etc.)

### Exemple d'utilisation

```tsx
import { incokalkAPI } from '../lib/api';

// Lister les expéditions
const { data } = await incokalkAPI.shipments.getAll({ status: 'IN_TRANSIT' });

// Scanner un article (PWA offline)
await incokalkAPI.receivings.scan(orderId, { barcode: '3760123456789', quantity: 1 });
```

---

## PWA (Progressive Web App)

Le frontend est une PWA avec :

- **Manifest** (`public/manifest.json`) : icônes, nom, thème
- **Service Worker** : mise en cache des ressources statiques
- **Mode hors ligne** : les scans de réception sont mis en file d'attente (`offlineQueue.ts`) et synchronisés au retour de la connexion
- **Install prompt** : composant `PWAInstallPrompt` pour encourager l'installation

---

## Tests

### Tests unitaires (Vitest)

```bash
cd frontend
npm test
```

### Tests E2E (Playwright)

```bash
cd frontend
npx playwright test

# Mode UI pour le débogage
npx playwright test --ui
```

### Configuration

- **Vitest** : `vitest.config.ts` — tests dans `src/__tests__/`
- **Playwright** : `playwright.config.ts` — tests dans `tests/`

---

## Design System

### Palette de couleurs

| Nom | Usage |
|---|---|
| `terracotta` (#C75B39) | Marque principale, boutons principaux |
| `medina` (#3D5A3D) | Texte principal, navigation |
| `sable` (#F5F0E8) | Fond clair |
| `zellige` | Motifs décoratifs (tuiles marocaines) |
| `olive` (#6B7B3A) | Accents secondaires |

### Composants UI

Les composants partagés sont dans `frontend/src/components/` :

- `Navbar.tsx` — Navigation principale avec menus déroulants
- `Footer.tsx` — Pied de page
- `Pagination.tsx` — Pagination réutilisable
- `LiveTrackingPanel.tsx` — Panneau de tracking temps réel
- `NotificationBell.tsx` — Notifications en temps réel
- `RoleBadge.tsx` — Affichage du rôle utilisateur
- `ErrorBoundary.tsx` — Gestion des erreurs React
- `OfflineIndicator.tsx` — Indicateur de connexion
- `MobileBottomNav.tsx` — Navigation mobile

---

## Points de vigilance

1. **Lazy loading** : toutes les pages sont lazy-loaded — ne pas oublier `React.lazy()` + `Suspense` pour toute nouvelle page
2. **Tenant header** : le `X-Tenant-ID` est ajouté automatiquement par l'interceptor Axios
3. **Refresh token** : le mécanisme de refresh est single-flight (un seul appel en cours à la fois)
4. **PWA offline** : les scans de réception sont stockés dans `localStorage` et synchronisés au retour du réseau
5. **Responsive** : la navbar desktop (md+) se transforme en menu hamburger sur mobile
6. **Rôles** : toujours vérifier `hasMinimumRole()` ou `isAdmin()` avant d'afficher des actions sensibles
7. **Tests** : les tests E2E Playwright couvrent les flux critiques (login, création expédition, scan réception)

---

## Contact & Support

Pour toute question technique sur le frontend, contactez l'équipe IncoKalk via Slack `#incokalk-frontend` ou ouvrez une issue sur GitHub.
