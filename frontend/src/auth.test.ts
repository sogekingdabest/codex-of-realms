import { beforeEach, describe, expect, it, vi } from 'vitest'

const keycloak = vi.hoisted(() => ({
  constructor: vi.fn(),
  init: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  updateToken: vi.fn(),
  clearToken: vi.fn(),
  token: 'access-token' as string | undefined,
  tokenParsed: { name: 'Dani' } as Record<string, unknown> | undefined,
}))

vi.mock('keycloak-js', () => ({
  default: class KeycloakMock {
    token = keycloak.token
    tokenParsed = keycloak.tokenParsed

    constructor(configuration: unknown) {
      keycloak.constructor(configuration)
    }

    init(options: unknown) {
      return keycloak.init(options)
    }

    login(options: unknown) {
      return keycloak.login(options)
    }

    logout(options: unknown) {
      return keycloak.logout(options)
    }

    updateToken(minValidity: number) {
      return keycloak.updateToken(minValidity)
    }

    clearToken() {
      keycloak.clearToken()
    }
  },
}))

import { createAuthSession } from './auth'

describe('createAuthSession', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    keycloak.token = 'access-token'
    keycloak.tokenParsed = { name: 'Dani' }
    keycloak.init.mockResolvedValue(true)
    keycloak.updateToken.mockResolvedValue(true)
    keycloak.login.mockResolvedValue(undefined)
    keycloak.logout.mockResolvedValue(undefined)
  })

  it('usa Authorization Code con PKCE S256 y mantiene el token en la sesión', async () => {
    const session = await createAuthSession()

    expect(keycloak.constructor).toHaveBeenCalledWith({
      url: 'http://localhost:8180',
      realm: 'codex-of-realms',
      clientId: 'codex-web',
    })
    expect(keycloak.init).toHaveBeenCalledWith({
      onLoad: 'login-required',
      flow: 'standard',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    })
    expect(session.displayName).toBe('Dani')
    await expect(session.getAccessToken()).resolves.toBe('access-token')
    expect(keycloak.updateToken).toHaveBeenCalledWith(30)
  })

  it('vuelve al proveedor de identidad si falla la renovación', async () => {
    const renewalError = new Error('expired')
    keycloak.updateToken.mockRejectedValue(renewalError)
    const session = await createAuthSession()

    await expect(session.getAccessToken()).rejects.toBe(renewalError)
    expect(keycloak.clearToken).toHaveBeenCalledOnce()
    expect(keycloak.login).toHaveBeenCalledWith({ redirectUri: window.location.href })
  })

  it('usa el nombre de usuario preferido cuando no existe un nombre completo', async () => {
    keycloak.tokenParsed = { preferred_username: 'cartografa' }

    const session = await createAuthSession()

    expect(session.displayName).toBe('cartografa')
  })

  it('usa un nombre neutral cuando el token no contiene identidad visible', async () => {
    keycloak.tokenParsed = undefined

    const session = await createAuthSession()

    expect(session.displayName).toBe('Explorador')
  })
})
