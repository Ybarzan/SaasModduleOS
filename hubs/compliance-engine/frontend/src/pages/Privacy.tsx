import Seo from '../components/Seo';
import Breadcrumbs from '../components/Breadcrumbs';

const LAST_UPDATED = '12 août 2026';

const SECTIONS = [
  {
    title: '1. Responsable du traitement',
    body: (
      <p>
        Les données personnelles collectées via IncoKalk sont traitées par la société éditrice d'IncoKalk
        (ci-après « nous », « IncoKalk »). Pour toute question relative à cette politique ou à l'exercice
        de vos droits, contactez-nous à <a href="mailto:contact@incokalk.com" className="text-accent hover:underline">contact@incokalk.com</a>.
      </p>
    ),
  },
  {
    title: '2. Données collectées',
    body: (
      <>
        <p className="mb-3">Nous collectons les catégories de données suivantes :</p>
        <ul className="list-disc pl-5 space-y-1.5">
          <li><strong>Données de compte</strong> : nom, adresse e-mail, mot de passe (chiffré), société, rôle.</li>
          <li><strong>Données d'usage</strong> : simulations réalisées, expéditions saisies, préférences de configuration.</li>
          <li><strong>Données de facturation</strong> : traitées par notre prestataire de paiement (Stripe) ; nous ne stockons pas les numéros de carte bancaire.</li>
          <li><strong>Données techniques</strong> : adresse IP, type de navigateur, journaux de connexion, à des fins de sécurité et de lutte contre la fraude.</li>
          <li><strong>Données de mesure d'audience</strong> : si Google Analytics est activé, des données de navigation anonymisées ou pseudonymisées (voir section Cookies).</li>
        </ul>
      </>
    ),
  },
  {
    title: '3. Finalités et bases légales du traitement',
    body: (
      <ul className="list-disc pl-5 space-y-1.5">
        <li><strong>Exécution du contrat</strong> : fourniture du service, gestion du compte, facturation.</li>
        <li><strong>Intérêt légitime</strong> : sécurité de la plateforme, prévention de la fraude, amélioration du produit.</li>
        <li><strong>Consentement</strong> : cookies de mesure d'audience non essentiels, communications marketing (désinscription possible à tout moment).</li>
        <li><strong>Obligation légale</strong> : conservation des données de facturation conformément aux obligations comptables et fiscales.</li>
      </ul>
    ),
  },
  {
    title: '4. Durée de conservation',
    body: (
      <p>
        Les données de compte sont conservées pendant toute la durée de la relation contractuelle, puis
        archivées ou supprimées conformément aux obligations légales applicables (notamment comptables et
        fiscales, généralement 10 ans pour les documents de facturation). Vous pouvez demander la
        suppression de votre compte à tout moment ; certaines données peuvent être conservées plus
        longtemps si une obligation légale l'impose.
      </p>
    ),
  },
  {
    title: '5. Destinataires des données',
    body: (
      <>
        <p className="mb-3">Vos données peuvent être partagées avec :</p>
        <ul className="list-disc pl-5 space-y-1.5">
          <li>Les membres autorisés de votre société sur la plateforme (selon les rôles et permissions configurés).</li>
          <li>Nos sous-traitants techniques (hébergement, envoi d'e-mails, paiement) agissant sur nos instructions et dans le cadre d'engagements contractuels de confidentialité.</li>
          <li>Les autorités compétentes, si la loi nous y oblige.</li>
        </ul>
        <p className="mt-3">Nous ne vendons jamais vos données personnelles à des tiers.</p>
      </>
    ),
  },
  {
    title: '6. Cookies et mesure d\'audience',
    body: (
      <p>
        IncoKalk utilise des cookies strictement nécessaires au fonctionnement du service (authentification,
        préférences de session). Si Google Analytics est activé sur ce déploiement, des cookies de mesure
        d'audience peuvent être déposés afin de comprendre l'usage du site ; vous pouvez vous y opposer via
        les paramètres de votre navigateur ou les outils de blocage de traceurs.
      </p>
    ),
  },
  {
    title: '7. Transferts internationaux',
    body: (
      <p>
        Certains de nos sous-traitants peuvent être situés hors de l'Union européenne. Dans ce cas, nous
        nous assurons de la mise en place de garanties appropriées (clauses contractuelles types de la
        Commission européenne ou décision d'adéquation) conformément au RGPD.
      </p>
    ),
  },
  {
    title: '8. Vos droits',
    body: (
      <>
        <p className="mb-3">
          Conformément au RGPD et à la loi Informatique et Libertés, vous disposez des droits suivants sur
          vos données personnelles :
        </p>
        <ul className="list-disc pl-5 space-y-1.5">
          <li>Droit d'accès et de rectification</li>
          <li>Droit à l'effacement (« droit à l'oubli »)</li>
          <li>Droit à la limitation du traitement</li>
          <li>Droit à la portabilité de vos données</li>
          <li>Droit d'opposition</li>
          <li>Droit d'introduire une réclamation auprès de la CNIL (ou de l'autorité de contrôle compétente)</li>
        </ul>
        <p className="mt-3">
          Pour exercer ces droits, contactez-nous à{' '}
          <a href="mailto:contact@incokalk.com" className="text-accent hover:underline">contact@incokalk.com</a>.
        </p>
      </>
    ),
  },
  {
    title: '9. Sécurité',
    body: (
      <p>
        Nous mettons en œuvre des mesures techniques et organisationnelles appropriées pour protéger vos
        données (chiffrement en transit et au repos, contrôle d'accès par rôles, journalisation des accès
        sensibles) afin de prévenir tout accès non autorisé, perte ou divulgation.
      </p>
    ),
  },
  {
    title: '10. Modifications de cette politique',
    body: (
      <p>
        Cette politique peut être mise à jour périodiquement. La date de dernière mise à jour figure en
        haut de cette page. En cas de modification substantielle, nous vous en informerons par e-mail ou
        via une notification sur la plateforme.
      </p>
    ),
  },
];

export default function Privacy() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-bg to-white">
      <Seo
        title="Politique de confidentialité"
        description="Politique de confidentialité IncoKalk : quelles données nous collectons, pourquoi, combien de temps, et comment exercer vos droits RGPD."
        path="/confidentialite"
      />
      <div className="container mx-auto px-4 py-10 max-w-3xl">
        <Breadcrumbs items={[{ label: 'Politique de confidentialité' }]} />

        <div className="my-10">
          <h1 className="text-4xl font-extrabold text-ink mb-3">Politique de confidentialité</h1>
          <p className="text-ink-soft">Dernière mise à jour : {LAST_UPDATED}</p>
        </div>

        <div className="bg-accent-soft border border-accent/30 rounded-2xl p-5 mb-10 text-sm text-ink-soft">
          <strong>Note :</strong> ce document constitue un modèle générique conforme aux grands principes du
          RGPD. Il doit être relu et adapté par un conseil juridique avant publication définitive, en
          fonction de vos sous-traitants réels, de votre implantation et de votre activité précise.
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
