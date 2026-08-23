import { Link } from 'react-router-dom';
import { ChevronRight, Home as HomeIcon } from 'lucide-react';
import { SITE_URL } from './Seo';

export interface BreadcrumbItem {
  label: string;
  to?: string;
}

// Fil d'Ariane avec micro-donnees BreadcrumbList (rich snippets Google).
export default function Breadcrumbs({ items }: { items: BreadcrumbItem[] }) {
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'Accueil', item: SITE_URL + '/' },
      ...items.map((item, i) => ({
        '@type': 'ListItem',
        position: i + 2,
        name: item.label,
        ...(item.to ? { item: SITE_URL + item.to } : {}),
      })),
    ],
  };

  return (
    <nav aria-label="Fil d'Ariane" className="text-sm">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />
      <ol className="flex flex-wrap items-center gap-1.5 text-ink-soft">
        <li className="flex items-center gap-1.5">
          <Link to="/" className="flex items-center gap-1 hover:text-accent transition-colors">
            <HomeIcon size={14} />
            Accueil
          </Link>
          <ChevronRight size={14} className="text-ink-soft/60" />
        </li>
        {items.map((item, i) => {
          const isLast = i === items.length - 1;
          return (
            <li key={item.label} className="flex items-center gap-1.5">
              {item.to && !isLast ? (
                <Link to={item.to} className="hover:text-accent transition-colors">
                  {item.label}
                </Link>
              ) : (
                <span className={isLast ? 'text-ink font-semibold' : ''} aria-current={isLast ? 'page' : undefined}>
                  {item.label}
                </span>
              )}
              {!isLast && <ChevronRight size={14} className="text-ink-soft/60" />}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
