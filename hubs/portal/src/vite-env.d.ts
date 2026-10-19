/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_COMPLIANCE_URL?: string;
  readonly VITE_FLEETHUB_URL?: string;
  readonly VITE_LOGISTIX_URL?: string;
  readonly VITE_PRAXIO_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
