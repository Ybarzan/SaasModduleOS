import { modules, type PraxioModule } from './modules';

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

export default function App() {
  return (
    <div className="min-h-screen bg-bg text-ink">
      <header className="border-b border-line">
        <div className="max-w-5xl mx-auto px-6 py-16">
          <div className="eyebrow">portail — v0.1</div>
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
          Transport et Logistix sont aujourd&apos;hui des sections de Compliance Engine — leur
          extraction en modules autonomes, connectés par API comme Fleet Hub, est en cours.
        </p>
      </footer>
    </div>
  );
}
