import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { modules, type PraxioModule } from './modules';
import Dashboard from './pages/Dashboard';

function StatusTag({ module }: { module: PraxioModule }) {
  const cls =
    module.status === 'live' ? 'tag-live' : module.status === 'integrated' ? 'tag-sim' : 'tag-soon';
  return (
    <span className={`tag ${cls}`}>
      <span className="tag-dot" />
      {module.statusLabel}
    </span>
  );
}

function ModuleCard({ module }: { module: PraxioModule }) {
  return (
    <a
      href={module.href}
      className="relative bg-surface border border-line p-6 flex flex-col gap-4 transition-colors duration-200 hover:border-accent/50"
    >
      <span className="hud-corner hud-corner-tl" />
      <span className="hud-corner hud-corner-tr" />
      <span className="hud-corner hud-corner-bl" />
      <span className="hud-corner hud-corner-br" />

      <div className="flex items-start justify-between gap-3">
        <h2 className="text-base font-bold uppercase tracking-[0.02em]">{module.name}</h2>
        <StatusTag module={module} />
      </div>
      <p className="text-sm text-ink-soft leading-relaxed flex-1">{module.tagline}</p>
      <span className="btn-primary self-start">&gt; ouvrir</span>
    </a>
  );
}

function Home() {
  return (
    <div className="min-h-screen bg-bg text-ink">
      <header className="border-b border-line">
        <div className="max-w-5xl mx-auto px-6 py-16">
          <div className="flex items-center justify-between gap-4">
            <div className="eyebrow">portail — v0.1</div>
            <Link to="/dashboard" className="eyebrow hover:text-accent-strong transition-colors">
              tableau de bord &rarr;
            </Link>
          </div>
          <h1 className="text-5xl sm:text-6xl font-extralight tracking-tight mt-2 mb-4">
            prax<b className="font-bold">io</b>
            <span className="cursor" aria-hidden="true" />
          </h1>
          <p className="max-w-xl text-sm text-ink-soft leading-relaxed">
            Un écosystème, plusieurs modules — conformité douanière, flotte, capacité de transport et
            logistique. Choisissez votre module ci-dessous.
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
        <p className="text-xs text-ink-soft leading-relaxed max-w-xl">
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
