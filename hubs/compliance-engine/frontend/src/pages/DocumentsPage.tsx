import { Link } from 'react-router-dom';
import { FileText, ScanText, Mail, ArrowRight } from 'lucide-react';
import { useAuthStore } from '../stores/auth';

const DocumentsPage = () => {
  const hasMinimumRole = useAuthStore((s) => s.hasMinimumRole);

  return (
    <div className="min-h-screen bg-bg">
      <div className="max-w-4xl mx-auto px-4 py-12">
        <div className="text-center mb-10">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-surface-2 mb-4">
            <FileText size={32} className="text-ink-soft" />
          </div>
          <h1 className="text-3xl sm:text-4xl font-extrabold text-ink mb-3">
            Documents
          </h1>
          <p className="text-ink-soft max-w-xl mx-auto">
            Outils d'extraction et d'automatisation documentaire.
          </p>
        </div>

        {/* Extraction/Import — visibles seulement si le rôle de l'utilisateur
            donne accès à ces pages (MANAGER / ADMIN). */}
        {hasMinimumRole('MANAGER') && (
          <Link
            to="/document-parser"
            className="flex items-center gap-4 bg-surface rounded-lg shadow-sm border border-line p-6 mb-6 hover:border-accent/40 transition-colors group"
          >
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-lg bg-accent/10 shrink-0">
              <ScanText size={24} className="text-accent" />
            </div>
            <div className="flex-1">
              <h3 className="font-bold text-ink mb-1">Extraction de documents</h3>
              <p className="text-sm text-ink-soft">
                Extrayez automatiquement les données d'une facture, d'un B/L ou d'un certificat scanné
              </p>
            </div>
            <ArrowRight size={18} className="text-ink-soft group-hover:text-accent transition-colors shrink-0" />
          </Link>
        )}
        {hasMinimumRole('ADMIN') && (
          <Link
            to="/email-intake"
            className="flex items-center gap-4 bg-surface rounded-lg shadow-sm border border-line p-6 mb-6 hover:border-accent/40 transition-colors group"
          >
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-lg bg-accent/10 shrink-0">
              <Mail size={24} className="text-accent" />
            </div>
            <div className="flex-1">
              <h3 className="font-bold text-ink mb-1">Import Email</h3>
              <p className="text-sm text-ink-soft">
                Configurez une boîte email pour créer des brouillons automatiquement
              </p>
            </div>
            <ArrowRight size={18} className="text-ink-soft group-hover:text-accent transition-colors shrink-0" />
          </Link>
        )}
      </div>
    </div>
  );
};

export default DocumentsPage;
