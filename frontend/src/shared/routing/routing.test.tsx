import { act, fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { Link, navigate, usePathname } from '.'

function CurrentPath() {
  return <output>{usePathname()}</output>
}

describe('routing', () => {
  it('navega sin recargar y sigue los botones atrás y adelante del navegador', () => {
    render(<><CurrentPath /><Link href="/universos/a/atlas">Atlas</Link></>)

    fireEvent.click(screen.getByRole('link', { name: 'Atlas' }))
    expect(screen.getByRole('status')).toHaveTextContent('/universos/a/atlas')

    act(() => {
      window.history.pushState(null, '', '/universos/a/consultas')
      window.dispatchEvent(new PopStateEvent('popstate'))
    })
    expect(screen.getByRole('status')).toHaveTextContent('/universos/a/consultas')
  })

  it('deja al navegador los clics modificados y los destinos externos', () => {
    const onClick = vi.fn()
    render(<><CurrentPath /><Link href="/destino" onClick={onClick}>Destino</Link><Link href="/fuera" target="_blank">Fuera</Link></>)

    const modified = new MouseEvent('click', { bubbles: true, cancelable: true, ctrlKey: true })
    screen.getByRole('link', { name: 'Destino' }).dispatchEvent(modified)
    expect(modified.defaultPrevented).toBe(false)
    expect(onClick).toHaveBeenCalledOnce()

    const external = new MouseEvent('click', { bubbles: true, cancelable: true })
    screen.getByRole('link', { name: 'Fuera' }).dispatchEvent(external)
    expect(external.defaultPrevented).toBe(false)
    expect(screen.getByRole('status')).toHaveTextContent(/^\/$/)
  })

  it('sustituye la entrada del historial cuando se pide y no duplica la actual', () => {
    const length = window.history.length
    act(() => navigate('/corregida', { replace: true }))
    expect(window.location.pathname).toBe('/corregida')
    expect(window.history).toHaveLength(length)
    act(() => navigate('/corregida'))
    expect(window.history).toHaveLength(length)
    act(() => navigate('/siguiente'))
    expect(window.history).toHaveLength(length + 1)
  })

  it('no deshace un enlace seguido después de calcular una corrección', () => {
    act(() => navigate('/universos/a/consultas'))
    act(() => navigate('/universos/a/fuentes', { replace: true, from: '/' }))
    expect(window.location.pathname).toBe('/universos/a/consultas')

    act(() => navigate('/universos/a/fuentes', { replace: true, from: '/universos/a/consultas' }))
    expect(window.location.pathname).toBe('/universos/a/fuentes')
  })
})
