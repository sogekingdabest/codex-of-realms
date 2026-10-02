import { describe, expect, it } from 'vitest'

import { visibilityOptionLabel } from './visibility'

describe('visibilityOptionLabel', () => {
  it('nombra igual cada visibilidad en todos los formularios', () => {
    expect(visibilityOptionLabel({ classification: 'PUBLIC', name: 'Público' })).toBe('Público')
    expect(visibilityOptionLabel({ classification: 'GM_ONLY', name: 'Solo dirección' })).toBe('Solo dirección')
    expect(visibilityOptionLabel({ classification: 'SPOILER', name: 'Recuerdos de Nara' })).toBe('Spoiler · Recuerdos de Nara')
  })

  it('conserva el nombre de una política base renombrada', () => {
    expect(visibilityOptionLabel({ classification: 'PUBLIC', name: 'Mundo abierto' })).toBe('Público · Mundo abierto')
  })
})
