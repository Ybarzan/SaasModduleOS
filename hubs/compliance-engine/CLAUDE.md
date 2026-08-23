## Health Stack

Monorepo: `backend/` (Java/Maven), `frontend/` (TypeScript/Vite), `mobile/` (TypeScript/Vite/Capacitor).

- backend test: `cd backend && mvn test`
- backend coverage: JaCoCo (configured in `backend/pom.xml`, report at `backend/target/site/jacoco/index.html`)
- backend static analysis: `cd backend && mvn spotbugs:check` (report: `mvn spotbugs:spotbugs`, then open `backend/target/site/spotbugs.html`; exclusions documented in `backend/spotbugs-exclude.xml`)
- frontend typecheck: `cd frontend && npx tsc --noEmit`
- frontend lint: `cd frontend && npm run lint` (eslint)
- frontend test: `cd frontend && npm run test` (vitest run)
- mobile typecheck: `cd mobile && npx tsc -b --noEmit`
- mobile lint: `cd mobile && npx oxlint`
- mobile test: `cd mobile && npm run test` (vitest run)
- dead code: knip not installed/configured
- shell lint: shellcheck not installed
