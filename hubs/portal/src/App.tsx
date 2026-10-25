import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { modules, type PraxioModule } from './modules';
import Dashboard from './pages/Dashboard';
import ThemeToggle from './components/ThemeToggle';

function StatusBadge({ module }: { module: PraxioModule }) {
  const cls = module.status === 'live' ? 'badge-live' : 'badge-sim';
  return (
    <span className={`badge ${cls}`}>
      <span className="badge-dot" />
      {module.statusLabel}
    </span>
  );
}

function ModuleCard({ module }: { module: PraxioModule }) {
  return (
    <a
      href={module.href}
      className="card p-6 flex flex-col gap-4 hover:border-accent/50 transition-colors duration-150"
    >
      <div className="flex items-start justify-between gap-3">
        <h2 className="text-base font-semibold">{module.name}</h2>
        <StatusBadge module={module} />
      </div>
      <p className="text-sm text-ink-soft leading-relaxed flex-1">{module.tagline}</p>
      <span className="btn-secondary self-start">
        Ouvrir
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="M5 12h14M13 6l6 6-6 6"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </span>
    </a>
  );
}

function Home() {
  return (
    <div className="min-h-screen bg-bg text-ink">
      <header className="border-b border-line">
        <div className="max-w-5xl mx-auto px-6 py-16">
          <div className="flex items-center justify-between gap-4">
            <span className="eyebrow">Portail</span>
            <div className="flex items-center gap-3">
              <Link to="/dashboard" className="text-sm font-medium text-ink-soft hover:text-ink transition-colors">
                Tableau de bord &rarr;
              </Link>
              <ThemeToggle />
            </div>
          </div>
          <h1 className="text-5xl font-semibold tracking-tight mt-3 mb-4">Praxio</h1>
          <p className="max-w-xl text-base text-ink-soft leading-relaxed">
            Un écosystème, plusieurs modules — conformité douanière, flotte, capacité de transport
            et logistique. Choisis ton module ci-dessous.
          </p>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-6 py-12">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
          {modules.map((m) => (
            <ModuleCard key={m.id} module={m} />
          ))}
        </div>
      </main>

      <footer className="max-w-5xl mx-auto px-6 py-10 border-t border-line">
        <p className="text-sm text-ink-soft leading-relaxed max-w-xl">
          Transport a été fusionné dans Fleet Hub et Logistix est désormais un module autonome,
          tous deux connectés aux autres modules par API plutôt que par base partagée.
        </p>
      </footer>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/dashboard" element={<Dashboard />} />
      </Routes>
    </BrowserRouter>
  );
}
