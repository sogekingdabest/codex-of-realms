export const runtimeConfig = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  keycloakUrl: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180',
  keycloakRealm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'codex-of-realms',
  keycloakClientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'codex-web',
} as const
