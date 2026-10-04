-- Quantité attendue par arrêt (reprise de la commande) : pré-remplit la saisie du chauffeur.
ALTER TABLE tour_stop ADD COLUMN expected_quantity INTEGER;
