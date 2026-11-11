# ADR-0004 : Identité commune des hubs (SSO)

- **Statut** : proposé — décision du propriétaire du produit attendue
- **Date** : 2026-11-05
- **Contexte** : audit fleet-hub du 04/11/2026, point 5

## Contexte

Chaque hub (fleet-hub, compliance-engine, Logistix, Finance) a ses propres comptes,
ses propres sociétés et son propre JWT. Le portail Praxio n'a **aucun compte** : un
seul mot de passe admin (Basic Auth) le protège, et son « SSO » rejoue des identifiants :

- il stocke **un seul jeu** d'identifiants par hub (`SsoCredentials`, ligne unique) :
  toute personne qui accède au portail ouvre chaque hub sous ce même compte ;
- les mots de passe sont stockés chiffrés **de façon réversible** pour être rejoués
  côté serveur (`POST {hub}/auth/login`) ;
- le compte relayé doit avoir la **2FA désactivée** (sinon le relais échoue) ;
- toutes les connexions partent de l'IP du portail : elles partagent la limite de débit
  et le verrouillage de compte des hubs.

Une même entreprise est donc plusieurs sociétés sans lien, reliées seulement par des
clés copiées-collées entre hubs (ADR-0003 limite les dégâts côté API, pas côté
utilisateurs).

## Critères

1. Un utilisateur = une identité, sur tous les hubs, avec sa 2FA.
2. Une entreprise cliente = un seul tenant reconnu par tous les hubs.
3. Aucun mot de passe stocké de façon réversible.
4. Hébergement UE (RGPD), coût d'exploitation compatible avec un petit VPS.
5. Migration progressive : chaque hub garde son login le temps de basculer.

## Options

### A. Fournisseur d'identité dédié auto-hébergé (Keycloak) — recommandée

Un service OIDC (Keycloak 26, ~500 Mo de RAM) émet les jetons ; chaque hub devient
« resource server » et valide le JWT via la clé publique (JWKS) du fournisseur.
L'entreprise cliente est une *Organization* Keycloak, dont l'identifiant est porté dans
le jeton et rattaché à la société locale de chaque hub (table de correspondance).

- ✅ Critères 1 à 5 ; 2FA, réinitialisation de mot de passe, sessions, révocation
  fournis et éprouvés ; standard OIDC, donc pas d'enfermement.
- ❌ Une brique de plus à exploiter (sauvegarde, mises à jour, supervision) et à sécuriser.
- Effort indicatif : 1 à 2 semaines pour fleet-hub + portail, puis quelques jours par hub.

### B. compliance-engine comme fournisseur d'identité

Piste évoquée dans `docs/07` : les autres hubs font confiance aux JWT de compliance-engine.

- ✅ Pas de nouveau service.
- ❌ compliance-engine n'est pas un serveur OIDC : il faudrait construire et maintenir
  émission, rotation de clés, JWKS, consentement, déconnexion globale. Le hub le plus
  gros devient un point de défaillance pour la connexion à tous les autres. Critère 2
  non résolu (ses sociétés ne sont pas celles des autres hubs).

### C. Fournisseur d'identité géré (Auth0, Microsoft Entra External ID, Zitadel Cloud…)

- ✅ Rien à exploiter ; mêmes avantages techniques que A.
- ❌ Coût par utilisateur actif, dépendance à un tiers, vérifier l'hébergement UE et le
  DPA. Zitadel Cloud (UE) est le plus proche de A sans exploitation.

### D. Statu quo durci

Identifiants SSO par utilisateur du portail au lieu d'une ligne unique, comptes de
portail réels.

- ✅ Le moins d'effort à court terme.
- ❌ Mots de passe toujours rejoués et stockés réversibles, 2FA toujours impossible pour
  les comptes relayés : ne règle pas le défaut, le déplace.

## Proposition

Option **A**, en trois étapes, sans bloquer le premier déploiement de fleet-hub :

1. fleet-hub accepte **en plus** les jetons du fournisseur d'identité (son login actuel
   reste) ; correspondance Organization → société ;
2. le portail se connecte via OIDC et **supprime le relais d'identifiants** (fin du
   stockage réversible et de la 2FA désactivée) ;
3. les autres hubs suivent ; les logins locaux sont retirés hub par hub.

Si l'exploitation d'un service de plus est exclue, **C** avec Zitadel Cloud (UE) est
l'alternative, avec le même plan.

## Ce qu'il faut décider

- A ou C (ou un autre choix) ;
- où se fait l'inscription d'une nouvelle entreprise : dans le fournisseur d'identité,
  ou toujours dans fleet-hub (qui crée alors l'Organization) ;
- le sort des comptes existants (migration avec réinitialisation de mot de passe, ou
  liaison au premier login).
