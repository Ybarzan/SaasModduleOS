import { useEffect } from 'react';

// Composant SEO reutilisable pour les pages publiques.
//
// index.html contient des balises meta/OG/Twitter STATIQUES par defaut (necessaires
// pour les robots qui ne chargent pas le JS : Facebook, Twitter/X, LinkedIn, Slack...).
// Ce composant les REMPLACE imperativement une fois React monte (au lieu de les
// re-rendre en JSX, ce qui creerait des balises dupliquees a cote des statiques --
// document.querySelector() et la plupart des parseurs ne lisent que la premiere
// occurrence, donc un doublon reviendrait a ignorer silencieusement le contenu
// par page). <title> est le seul cas ou le rendu JSX seul suffit : le DOM ne
// permet qu'un seul <title>, React le remplace nativement sans dupliquer.
const SITE_NAME = 'IncoKalk';
const SITE_URL = 'https://www.incokalk.com';
const DEFAULT_DESCRIPTION =
  'IncoKalk — Simulateur Incoterms 2020, comparaison de devis transport et gestion des expéditions internationales pour exportateurs et importateurs.';
const DEFAULT_OG_IMAGE = `${SITE_URL}/og-image.png`;

interface SeoProps {
  title: string;
  description?: string;
  path: string;
  ogImage?: string;
  ogType?: 'website' | 'article';
  noindex?: boolean;
  jsonLd?: Record<string, unknown> | Record<string, unknown>[];
}

function setMeta(attr: 'name' | 'property', key: string, content: string) {
  let el = document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${key}"]`);
  if (!el) {
    el = document.createElement('meta');
    el.setAttribute(attr, key);
    document.head.appendChild(el);
  }
  el.setAttribute('content', content);
}

function removeMeta(attr: 'name' | 'property', key: string) {
  document.head.querySelector(`meta[${attr}="${key}"]`)?.remove();
}

function setCanonical(href: string) {
  let el = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
  if (!el) {
    el = document.createElement('link');
    el.setAttribute('rel', 'canonical');
    document.head.appendChild(el);
  }
  el.setAttribute('href', href);
}

export default function Seo({
  title,
  description = DEFAULT_DESCRIPTION,
  path,
  ogImage = DEFAULT_OG_IMAGE,
  ogType = 'website',
  noindex = false,
  jsonLd,
}: SeoProps) {
  const fullTitle = title.includes(SITE_NAME) ? title : `${title} | ${SITE_NAME}`;
  const canonical = `${SITE_URL}${path}`;

  useEffect(() => {
    setMeta('name', 'description', description);
    setCanonical(canonical);
    if (noindex) {
      setMeta('name', 'robots', 'noindex, nofollow');
    } else {
      removeMeta('name', 'robots');
    }

    setMeta('property', 'og:title', fullTitle);
    setMeta('property', 'og:description', description);
    setMeta('property', 'og:type', ogType);
    setMeta('property', 'og:url', canonical);
    setMeta('property', 'og:image', ogImage);

    setMeta('name', 'twitter:title', fullTitle);
    setMeta('name', 'twitter:description', description);
    setMeta('name', 'twitter:image', ogImage);
  }, [fullTitle, description, canonical, ogImage, ogType, noindex]);

  useEffect(() => {
    if (!jsonLd) return;
    const schemas = Array.isArray(jsonLd) ? jsonLd : [jsonLd];
    const nodes = schemas.map((schema) => {
      const script = document.createElement('script');
      script.type = 'application/ld+json';
      script.textContent = JSON.stringify(schema);
      document.head.appendChild(script);
      return script;
    });
    return () => {
      nodes.forEach((n) => n.remove());
    };
  }, [jsonLd]);

  return <title>{fullTitle}</title>;
}

export { SITE_NAME, SITE_URL, DEFAULT_DESCRIPTION, DEFAULT_OG_IMAGE };
