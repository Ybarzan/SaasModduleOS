import { Loader2 } from 'lucide-react';
import Modal from './Modal';

interface DeleteConfirmModalProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  message: string;
  title?: string;
  confirmLabel?: string;
  isPending?: boolean;
}

/**
 * Le "confirmer la suppression : titre + message + Annuler/Supprimer" était
 * réimplémenté à chaque page qui supprime quelque chose, chacune sans piège de
 * focus ni fermeture Échap (voir Modal.tsx, ajouté juste avant celui-ci pour
 * cette raison). Bâti sur Modal pour ne pas dupliquer cette plomberie une
 * troisième fois.
 */
const DeleteConfirmModal = ({
  open,
  onClose,
  onConfirm,
  message,
  title = 'Confirmer la suppression',
  confirmLabel = 'Supprimer',
  isPending = false,
}: DeleteConfirmModalProps) => (
  <Modal open={open} onClose={onClose} ariaLabel={title} maxWidth="max-w-md">
    <div className="p-6">
      <h3 className="text-lg font-bold text-ink mb-4">{title}</h3>
      <p className="text-ink-soft mb-6">{message}</p>
      <div className="flex justify-end space-x-3">
        <button
          onClick={onClose}
          className="px-4 py-2 text-ink bg-surface-2 rounded-lg hover:bg-surface-2 transition-colors"
        >
          Annuler
        </button>
        <button
          onClick={onConfirm}
          disabled={isPending}
          className="px-4 py-2 bg-danger text-white rounded-lg hover:bg-danger/90 transition-colors disabled:opacity-50 flex items-center space-x-2"
        >
          {isPending && <Loader2 className="h-4 w-4 animate-spin" />}
          <span>{confirmLabel}</span>
        </button>
      </div>
    </div>
  </Modal>
);

export default DeleteConfirmModal;
