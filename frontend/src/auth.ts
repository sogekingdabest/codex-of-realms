import Keycloak from 'keycloak-js'

import { runtimeConfig } from './config'

export interface AuthSession {
  displayName: string
  getAccessToken: () => Promise<string>
  logout: () => Promise<void>
}

export async function createAuthSession(): Promise<AuthSession> {
  const keycloak = new Keycloak({
    url: runtimeConfig.keycloakUrl,
    realm: runtimeConfig.keycloakRealm,
    clientId: runtimeConfig.keycloakClientId,
  })

  const authenticated = await keycloak.init({
    onLoad: 'login-required',
    flow: 'standard',
    pkceMethod: 'S256',
    checkLoginIframe: false,
  })

  if (!authenticated) {
    await keycloak.login({ redirectUri: window.location.href })
    throw new Error('Keycloak no pudo completar el inicio de sesión.')
  }

  const claims = keycloak.tokenParsed
  const displayName =
    typeof claims?.name === 'string'
      ? claims.name
      : typeof claims?.preferred_username === 'string'
        ? claims.preferred_username
        : 'Explorador'

  return {
    displayName,
    async getAccessToken() {
      try {
        await keycloak.updateToken(30)
      } catch (error) {
        keycloak.clearToken()
        await keycloak.login({ redirectUri: window.location.href })
        throw error
      }

      if (!keycloak.token) {
        throw new Error('La sesión no contiene un access token válido.')
      }
      return keycloak.token
    },
    async logout() {
      await keycloak.logout({ redirectUri: window.location.origin })
    },
  }
}
