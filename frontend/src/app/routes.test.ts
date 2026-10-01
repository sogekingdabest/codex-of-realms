import { describe, expect, it } from 'vitest'

import type { RealmSummary } from '../features/realm'
import { canOpenSection, parseRoute, resolveRoute, routePath, sectionRoute, type WorkspaceRoute } from './routes'

const realms: RealmSummary[] = [
  { id: 'realm-1', name: 'El Meridiano', role: 'OWNER' },
  { id: 'realm-2', name: 'La Frontera', role: 'PLAYER' },
]

describe('routes', () => {
  it.each<[string, WorkspaceRoute]>([
    ['/universos/realm-1/fuentes', { realmId: 'realm-1', section: 'sources', sourceId: null }],
    ['/universos/realm-1/fuentes/source-1', { realmId: 'realm-1', section: 'sources', sourceId: 'source-1' }],
    ['/universos/realm-1/atlas', { realmId: 'realm-1', section: 'canon', view: 'entities', entityId: null }],
    ['/universos/realm-1/atlas/entity-1', { realmId: 'realm-1', section: 'canon', view: 'entities', entityId: 'entity-1' }],
    ['/universos/realm-1/atlas/relaciones', { realmId: 'realm-1', section: 'canon', view: 'relations', entityId: null }],
    ['/universos/realm-1/consultas', { realmId: 'realm-1', section: 'questions' }],
    ['/universos/realm-1/personas', { realmId: 'realm-1', section: 'access' }],
    ['/universos/a%2Fb/fuentes/c%20d', { realmId: 'a/b', section: 'sources', sourceId: 'c d' }],
  ])('lee y vuelve a escribir %s', (path, route) => {
    expect(parseRoute(path)).toEqual(route)
    expect(routePath(route)).toBe(path)
  })

  it('abre las fuentes cuando la dirección solo nombra el universo', () => {
    expect(parseRoute('/universos/realm-1/')).toEqual(sectionRoute('realm-1', 'sources'))
  })

  it.each([
    '/', '/otra/realm-1', '/universos', '/universos/realm-1/mapas',
    '/universos/realm-1/consultas/extra', '/universos/realm-1/fuentes/a/b', '/universos/%E0%A4%A/fuentes',
  ])('no reconoce %s', (path) => {
    expect(parseRoute(path)).toBeNull()
  })

  it('redirige a un lugar accesible cuando la dirección no lo es', () => {
    expect(resolveRoute(parseRoute('/universos/realm-1/atlas/entity-1'), realms))
      .toEqual({ realmId: 'realm-1', section: 'canon', view: 'entities', entityId: 'entity-1' })
    expect(resolveRoute(parseRoute('/universos/desconocido/consultas'), realms)).toEqual(sectionRoute('realm-1', 'sources'))
    expect(resolveRoute(null, realms)).toEqual(sectionRoute('realm-1', 'sources'))
    expect(resolveRoute(parseRoute('/universos/realm-2/personas'), realms)).toEqual(sectionRoute('realm-2', 'sources'))
    expect(resolveRoute(null, [])).toBeNull()
  })

  it('reserva personas y permisos a propietarios y editores', () => {
    expect(canOpenSection('OWNER', 'access')).toBe(true)
    expect(canOpenSection('EDITOR', 'access')).toBe(true)
    expect(canOpenSection('PLAYER', 'access')).toBe(false)
    expect(canOpenSection('PLAYER', 'canon')).toBe(true)
  })
})
