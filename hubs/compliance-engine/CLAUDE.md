## Health Stack

Monorepo: `backend/` (Java/Maven), `frontend/` (TypeScript/Vite), `mobile/` (TypeScript/Vite/Capacitor), `embeddings-service/` (Python/FastAPI).

- backend test: `cd backend && mvn test`
- backend coverage: JaCoCo (configured in `backend/pom.xml`, report at `backend/target/site/jacoco/index.html`)
- backend static analysis: `cd backend && mvn spotbugs:check` (report: `mvn spotbugs:spotbugs`, then open `backend/target/site/spotbugs.html`; exclusions documented in `backend/spotbugs-exclude.xml`)
- frontend typecheck: `cd frontend && npx tsc --noEmit`
- frontend lint: `cd frontend && npm run lint` (eslint)
- frontend test: `cd frontend && npm run test` (vitest run)
- mobile typecheck: `cd mobile && npx tsc -b --noEmit`
- mobile lint: `cd mobile && npx oxlint`
- mobile test: `cd mobile && npm run test` (vitest run)
- embeddings-service test: `cd embeddings-service && pip install -r requirements-dev.txt && python -m pytest tests/ -v` (mocks sentence-transformers via `conftest.py` — no model download, no torch needed for this fast unit-test run; the real model is only exercised when the Docker image is built)
- dead code: knip not installed/configured
- shell lint: shellcheck not installed
