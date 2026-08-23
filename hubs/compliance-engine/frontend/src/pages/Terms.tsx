import Seo from '../components/Seo';
import Breadcrumbs from '../components/Breadcrumbs';

const LAST_UPDATED = '12 août 2026';

const SECTIONS = [
  {
    title: '1. Objet',
    body: (
      <p>
        Les présentes Conditions Générales d'Utilisation (« CGU ») ont pour objet de définir les
        modalités et conditions dans lesquelles [À COMPLÉTER — raison sociale de la société éditrice]
        met à disposition la plateforme IncoKalk (le « Service ») et les conditions dans lesquelles
        l'utilisateur (« vous », « l'Utilisateur ») l'utilise. Toute inscription ou utilisation du
        Service implique l'acceptation sans réserve des présentes CGU.
      </p>
    ),
  },
  {
    title: '2. Description du service',
    body: (
      <p>
        IncoKalk est un logiciel en ligne (SaaS) de calcul de coûts logistiques internationaux selon
        les Incoterms 2020, de comparaison de devis transporteurs et de gestion des expéditions. Le
        détail des fonctionnalités par plan est disponible sur la page{' '}
        <a href="/pricing" className="text-accent hover:underline">Tarifs</a>.
      </p>
    ),
  },
  {
    title: '3. Inscription et compte',
    body: (
      <>
        <p className="mb-3">
          L'accès au Service nécessite la création d'un compte. L'Utilisateur s'engage à fournir des
          informations exactes et à jour, et à préserver la confidentialité de ses identifiants de
          connexion. Toute action réalisée depuis un compte est réputée effectuée par son titulaire.
        </p>
        <p>
          Un plan d'essai gratuit peut être proposé (durée et limites précisées lors de l'inscription
          et sur la page Tarifs). L'éditeur se réserve le droit de suspendre un compte en cas d'usage
          frauduleux ou de non-respect des présentes CGU.
        </p>
      </>
    ),
  },
  {
    title: '4. Abonnement, tarifs et facturation',
    body: (
      <>
        <p className="mb-3">
          Les plans payants sont facturés selon la périodicité choisie (mensuelle ou annuelle) via
          notre prestataire de paiement Stripe. Les tarifs en vigueur sont ceux affichés sur la page{' '}
          <a href="/pricing" className="text-accent hover:underline">Tarifs</a> au moment de
          la souscription.
        </p>
        <p className="mb-3">
          L'Utilisateur peut changer de plan ou résilier son abonnement à tout moment depuis son
          espace de facturation ; le changement est proratisé sur la période en cours. Sauf mention
          contraire, les sommes déjà versées ne sont pas remboursables.
        </p>
        <p>
          En cas d'impayé, l'accès aux fonctionnalités payantes peut être suspendu jusqu'à
          régularisation.
        </p>
      </>
    ),
  },
  {
    title: '5. Obligations de l\'utilisateur',
    body: (
      <>
        <p className="mb-3">L'Utilisateur s'engage à :</p>
        <ul className="list-disc pl-5 space-y-1.5">
          <li>Utiliser le Service conformément à sa destination et aux lois applicables ;</li>
          <li>Ne pas tenter de contourner les mesures de sécurité ou d'accéder à des données ne lui appartenant pas ;</li>
          <li>Ne pas revendre, sous-licencier ou redistribuer le Service sans autorisation écrite préalable ;</li>
          <li>Garantir l'exactitude des données saisies (expéditions, tarifs, informations douanières).</li>
        </ul>
      </>
    ),
  },
  {
    title: '6. Propriété intellectuelle',
    body: (
      <p>
        Le Service, son code source, son design et sa marque restent la propriété exclusive de
        l'éditeur. Aucune disposition des présentes CGU ne confère à l'Utilisateur un quelconque
        droit de propriété intellectuelle sur le Service. Les données saisies par l'Utilisateur dans
        le cadre de son usage du Service restent sa propriété ; il en conserve le contrôle et peut en
        demander l'export ou la suppression conformément à notre{' '}
        <a href="/confidentialite" className="text-accent hover:underline">politique de confidentialité</a>.
      </p>
    ),
  },
  {
    title: '7. Disponibilité et limitation de responsabilité',
    body: (
      <p>
        L'éditeur met en œuvre les moyens raisonnables pour assurer la disponibilité et la sécurité du
        Service, sans garantie de disponibilité continue (maintenances planifiées, incidents
        techniques indépendants de sa volonté). Les données de calcul (droits de douane, taux de
        change, estimations tarifaires) sont fournies à titre indicatif et ne dispensent pas
        l'Utilisateur de vérifier ses obligations douanières et réglementaires auprès des autorités
        compétentes. Dans les limites permises par la loi, la responsabilité de l'éditeur ne saurait
        être engagée au-delà des sommes effectivement versées par l'Utilisateur au cours des douze
        derniers mois.
      </p>
    ),
  },
  {
    title: '8. Données personnelles',
    body: (
      <p>
        Le traitement des données personnelles est décrit dans notre{' '}
        <a href="/confidentialite" className="text-accent hover:underline">politique de confidentialité</a>,
        qui fait partie intégrante des présentes CGU.
      </p>
    ),
  },
  {
    title: '9. Résiliation',
    body: (
      <p>
        L'Utilisateur peut résilier son compte à tout moment depuis les paramètres de son compte.
        L'éditeur peut résilier ou suspendre un compte en cas de manquement grave ou répété aux
        présentes CGU, après notification lorsque cela est raisonnablement possible. La résiliation
        entraîne la cessation d'accès au Service ; les modalités de conservation puis de suppression
        des données sont décrites dans la politique de confidentialité.
      </p>
    ),
  },
  {
    title: '10. Modification des CGU',
    body: (
      <p>
        Les présentes CGU peuvent être mises à jour périodiquement. La date de dernière mise à jour
        figure en haut de cette page. En cas de modification substantielle, les Utilisateurs actifs en
        seront informés par e-mail ou notification sur la plateforme avant leur entrée en vigueur.
      </p>
    ),
  },
  {
    title: '11. Droit applicable et litiges',
    body: (
      <p>
        Les présentes CGU sont soumises au droit français. En cas de litige, une solution amiable sera
        recherchée en priorité ; à défaut, les tribunaux compétents seront ceux du ressort du siège
        social de l'éditeur, sous réserve des règles impératives applicables aux consommateurs le cas
        échéant.
      </p>
    ),
  },
  {
    title: '12. Contact',
    body: (
      <p>
        Pour toute question relative aux présentes CGU, contactez-nous à{' '}
        <a href="mailto:contact@incokalk.com" className="text-accent hover:underline">contact@incokalk.com</a>.
      </p>
    ),
  },
];

export default function Terms() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-bg to-white">
      <Seo
        title="Conditions Générales d'Utilisation"
        description="Conditions Générales d'Utilisation d'IncoKalk : inscription, abonnement, facturation, obligations et responsabilités."
        path="/cgu"
      />
      <div className="container mx-auto px-4 py-10 max-w-3xl">
        <Breadcrumbs items={[{ label: 'Conditions Générales d\'Utilisation' }]} />

        <div className="my-10">
          <h1 className="text-4xl font-extrabold text-ink mb-3">
            Conditions Générales d'Utilisation
          </h1>
          <p className="text-ink-soft">Dernière mise à jour : {LAST_UPDATED}</p>
        </div>

        <div className="bg-accent-soft border border-accent/30 rounded-2xl p-5 mb-10 text-sm text-ink-soft">
          <strong>Note :</strong> ce document constitue un modèle générique de CGU pour un SaaS B2B. Il
          contient des champs à compléter avec les informations réelles de la société éditrice, et doit
          être relu et adapté par un conseil juridique avant publication définitive.
        </div>

        <div className="space-y-10">
          {SECTIONS.map((section) => (
            <section key={section.title}>
              <h2 className="text-xl font-bold text-ink mb-3">{section.title}</h2>
              <div className="text-ink-soft leading-relaxed">{section.body}</div>
            </section>
          ))}
        </div>
      </div>
    </div>
  );
}
