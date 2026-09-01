import { describe, expect, it } from 'vitest'

import { errorMessage, isAbortError } from './errors'
import { formText } from './forms'

describe('shared lib', () => {
  it('normaliza errores desconocidos y reconoce cancelaciones', () => {
    expect(errorMessage(new Error('fallo conocido'))).toBe('fallo conocido')
    expect(errorMessage('fallo opaco')).toBe('Ha ocurrido un error inesperado.')
    expect(isAbortError(new DOMException('cancelado', 'AbortError'))).toBe(true)
    expect(isAbortError(new Error('otro error'))).toBe(false)
  })

  it('recorta texto de formularios e ignora valores que no son texto', () => {
    const form = new FormData()
    form.set('name', '  Meridiano  ')
    form.set('file', new File(['data'], 'source.txt'))

    expect(formText(form, 'name')).toBe('Meridiano')
    expect(formText(form, 'file')).toBe('')
    expect(formText(form, 'missing')).toBe('')
  })
})
