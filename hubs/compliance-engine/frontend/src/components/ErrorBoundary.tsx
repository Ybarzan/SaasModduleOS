import { Component, type ReactNode } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export default class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error('ErrorBoundary caught:', error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback;

      return (
        <div className="min-h-[400px] flex flex-col items-center justify-center p-8 text-center">
          <AlertTriangle className="text-warning mb-4" size={48} />
          <h2 className="text-xl font-semibold text-ink mb-2">
            Une erreur est survenue
          </h2>
          <p className="text-ink-soft mb-6 max-w-md">
            {this.state.error?.message || 'Une erreur inattendue s\'est produite.'}
          </p>
          <button
            onClick={() => {
              this.setState({ hasError: false, error: null });
              window.location.reload();
            }}
            className="flex items-center gap-2 px-4 py-2 bg-ink text-white rounded-none hover:bg-ink/90 transition-colors"
          >
            <RefreshCw size={16} />
            Recharger la page
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
