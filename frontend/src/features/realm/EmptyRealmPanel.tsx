import type { SubmitEvent } from 'react'

export function EmptyRealmPanel({ creating, onSubmit }: Readonly<{
  creating: boolean
  onSubmit: (event: SubmitEvent<HTMLFormElement>) => Promise<void>
}>) {
  return (
    <section className="empty-realm panel">
      <span className="panel-number">01</span>
      <div>
        <p className="eyebrow">Primer registro</p>
        <h2>Crea un universo</h2>
        <p>Será tu espacio aislado para fuentes, permisos y respuestas.</p>
        <form onSubmit={(event) => void onSubmit(event)}>
          <label>
            <span>Nombre del universo</span>
            <input name="realmName" maxLength={120} required placeholder="El Meridiano" />
          </label>
          <button disabled={creating} type="submit">{creating ? 'Creando…' : 'Crear universo'}</button>
        </form>
      </div>
    </section>
  )
}
