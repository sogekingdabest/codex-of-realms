import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { ComponentProps } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { playerAudience, type VisibilityAudience } from '../../shared/lib/visibility'
import { usePathname } from '../../shared/routing'
import { CatalogueWorkspace } from './CatalogueWorkspace'
import type { CatalogueLocation, LoreEntityView, LoreRelationView } from './model'

type CatalogueProps = Omit<ComponentProps<typeof CatalogueWorkspace>, 'location' | 'hrefFor' | 'layout' | 'audience'>

const editorAudience: VisibilityAudience = { viewer: 'editor', revealedTo: () => ['Tala'], policyName: () => 'Público' }

function catalogueHref({ view, entityId }: CatalogueLocation) {
  if (view === 'relations') return '/atlas/relaciones'
  return entityId ? `/atlas/${entityId}` : '/atlas'
}

/** Drives the catalogue from the address bar, as the app does. */
function RoutedCatalogue(props: CatalogueProps) {
  const [, root, item] = usePathname().split('/')
  const location: CatalogueLocation = root !== 'atlas' || !item ? { view: 'entities', entityId: null }
    : item === 'relaciones' ? { view: 'relations', entityId: null } : { view: 'entities', entityId: item }
  return <CatalogueWorkspace {...props} audience={props.canEdit ? editorAudience : playerAudience} location={location} hrefFor={catalogueHref}
    layout={({ index, content }) => <>{index}{content}</>} />
}

const publicPolicy = {
  id: 'policy-public',
  realmId: 'realm-1',
  classification: 'PUBLIC' as const,
  name: 'Público',
  description: null,
}

const source = {
  id: 'source-1',
  realmId: 'realm-1',
  title: 'Atlas público',
  versionId: 'version-1',
  versionNumber: 1,
  checksumSha256: 'a'.repeat(64),
  originalFilename: 'atlas.md',
  mediaType: 'text/markdown',
  language: 'es',
  status: 'READY' as const,
  accessPolicyId: 'policy-public',
  visibility: 'PUBLIC' as const,
  embeddingProvider: 'test',
  embeddingModel: 'test',
  embeddingDimension: 8,
  chunkCount: 1,
  createdAt: '2026-08-29T10:00:00Z',
}

function entity(id: string, name: string, status: 'PROPOSED' | 'CANON' = 'PROPOSED'): LoreEntityView {
  return {
    id,
    realmId: 'realm-1',
    type: 'CHARACTER',
    displayName: name,
    aliases: [],
    description: `${name} custodia el paso.`,
    canonStatus: status,
    accessPolicyId: 'policy-public',
    visibility: 'PUBLIC',
    sourceEvidence: [],
    createdBy: 'owner-1',
    createdAt: '2026-08-29T10:00:00Z',
    updatedBy: 'owner-1',
    updatedAt: '2026-08-29T10:00:00Z',
    promotedBy: status === 'CANON' ? 'owner-1' : null,
    promotedAt: status === 'CANON' ? '2026-08-29T10:05:00Z' : null,
    promotionHistory: [],
  }
}

function relation(): LoreRelationView {
  return {
    id: 'relation-1',
    realmId: 'realm-1',
    sourceEntityId: 'entity-1',
    sourceEntityName: 'Nara Vey',
    targetEntityId: 'entity-2',
    targetEntityName: 'Lumbrevela',
    relationType: 'VIVE_EN',
    description: 'Nara vive en Lumbrevela.',
    canonStatus: 'CANON',
    accessPolicyId: 'policy-public',
    visibility: 'PUBLIC',
    sourceEvidence: [],
    createdBy: 'owner-1',
    createdAt: '2026-08-29T10:00:00Z',
    updatedBy: 'owner-1',
    updatedAt: '2026-08-29T10:00:00Z',
    promotedBy: 'owner-1',
    promotedAt: '2026-08-29T10:05:00Z',
    promotionHistory: [],
  }
}

function catalogueApi(entities: LoreEntityView[] = [], relations: LoreRelationView[] = []) {
  return {
    listSources: vi.fn(),
    uploadSource: vi.fn(),
    replaceSource: vi.fn(),
    reprocessSource: vi.fn(),
    listSourceJobs: vi.fn().mockResolvedValue([]),
    getSourceJob: vi.fn(),
    retrySourceJob: vi.fn(),
    deleteSource: vi.fn(),
    getSourceContent: vi.fn().mockResolvedValue({ content: 'Nara Vey custodia el paso oriental de Lumbrevela.' }),
    listLoreEntities: vi.fn().mockResolvedValue(entities),
    listLoreRelations: vi.fn().mockResolvedValue(relations),
    listSourceChunks: vi.fn().mockResolvedValue([
      {
        id: 'chunk-1',
        documentId: 'source-1',
        documentVersionId: 'version-1',
        sourceTitle: 'Atlas público',
        ordinal: 0,
        heading: 'Nara Vey',
        content: 'Nara Vey custodia el paso oriental de Lumbrevela.',
        startOffset: 12,
        endOffset: 64,
      },
    ]),
    createLoreEntity: vi.fn(),
    updateLoreEntity: vi.fn(),
    promoteLoreEntity: vi.fn(),
    deleteLoreEntity: vi.fn(),
    createLoreRelation: vi.fn(),
    updateLoreRelation: vi.fn(),
    promoteLoreRelation: vi.fn(),
    deleteLoreRelation: vi.fn(),
  }
}

describe('CatalogueWorkspace', () => {
  it('presenta sellos, tipos, visibilidad y relaciones como frases', async () => {
    const secretRelation = {
      ...relation(), id: 'relation-2', targetEntityId: 'entity-3', targetEntityName: 'El Meridiano',
      relationType: 'SE_DESPLAZA_RESPECTO_A', description: 'Es la ciudad la que se mueve.', visibility: 'GM_ONLY' as const,
    }
    const api = catalogueApi(
      [entity('entity-1', 'Nara Vey', 'CANON'), entity('entity-2', 'Lumbrevela'), entity('entity-3', 'El Meridiano')],
      [{ ...relation(), canonStatus: 'PROPOSED' }, secretRelation],
    )
    render(<RoutedCatalogue contentApi={api} loreApi={api} canEdit policies={[publicPolicy]} realmId="realm-1" sources={[]} onOpenEvidence={vi.fn()} />)

    expect(await screen.findByRole('img', { name: 'Sello de canon' })).toBeInTheDocument()
    expect(screen.getByText('Personaje', { selector: '.entity-type' })).toBeInTheDocument()
    expect(screen.getByText('se desplaza respecto a')).toHaveClass('relation-verb')
    expect(screen.getByText('Solo dirección')).toHaveClass('visibility-gm')
    expect(screen.getByRole('link', { name: 'Ver ficha de El Meridiano' })).toHaveAttribute('href', '/atlas/entity-3')
    expect(screen.getByRole('button', { name: 'Retirar' })).toHaveClass('danger-button')

    const lumbrevela = screen.getByRole('link', { name: 'Abrir ficha de Lumbrevela' })
    expect(within(lumbrevela).getByRole('img', { name: 'Propuesto' })).toBeInTheDocument()
    expect(within(lumbrevela).getByRole('img', { name: 'Público' })).toBeInTheDocument()

    fireEvent.click(lumbrevela)
    expect(await screen.findByRole('img', { name: 'Sello de propuesta' })).toBeInTheDocument()
  })

  it('navega de una relación a su ficha exacta y permite volver a las relaciones', async () => {
    const api = catalogueApi([entity('entity-1', 'Nara Vey'), entity('entity-2', 'Lumbrevela')], [relation()])
    render(<RoutedCatalogue contentApi={api} loreApi={api} canEdit policies={[publicPolicy]} realmId="realm-1" sources={[]} onOpenEvidence={vi.fn()} />)
    await screen.findByRole('heading', { name: 'Nara Vey' })
    fireEvent.click(screen.getByRole('link', { name: 'Relaciones' }))
    fireEvent.click(screen.getByRole('link', { name: 'Ver ficha de Lumbrevela' }))
    expect(screen.getByText('Lumbrevela custodia el paso.')).toBeVisible()
    expect(screen.queryByText('Nara Vey custodia el paso.')).not.toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Lumbrevela' })).toHaveFocus())
    expect(screen.queryByRole('heading', { name: 'Registrar concepto' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: 'Relaciones' }))
    expect(screen.getByText('Nara vive en Lumbrevela.')).toBeVisible()
    fireEvent.click(screen.getByRole('link', { name: 'Ver ficha de Nara Vey' }))
    expect(screen.getByText('Nara Vey custodia el paso.')).toBeVisible()
    fireEvent.click(screen.getByRole('link', { name: 'Abrir ficha de Lumbrevela' }))
    expect(screen.getByText('Lumbrevela custodia el paso.')).toBeVisible()
  })

  it('ofrece a un jugador únicamente el catálogo visible en modo lectura', async () => {
    const api = catalogueApi([entity('entity-1', 'Nara Vey', 'CANON')], [relation()])

    render(
      <RoutedCatalogue
        contentApi={api}
        loreApi={api}
        canEdit={false}
        policies={[]}
        realmId="realm-1"
        sources={[]}
        onOpenEvidence={vi.fn()}
      />,
    )

    expect(await screen.findByRole('heading', { name: 'Nara Vey' })).toBeInTheDocument()
    expect(screen.queryByText('Registrar concepto')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Editar' })).not.toBeInTheDocument()
    expect(api.listLoreEntities).toHaveBeenCalledWith('realm-1', expect.any(AbortSignal))

    fireEvent.click(screen.getByRole('link', { name: 'Relaciones' }))
    expect(await screen.findByText('Nara vive en Lumbrevela.')).toBeInTheDocument()
  })

  it('crea una propuesta editorial con un fragmento visible como evidencia', async () => {
    const api = catalogueApi()
    const created = entity('entity-new', 'Nara Vey')
    created.sourceEvidence = [
      {
        documentId: 'source-1',
        documentVersionId: 'version-1',
        chunkId: 'chunk-1',
        sourceTitle: 'Atlas público',
        checksumSha256: 'a'.repeat(64),
        heading: 'Nara Vey',
        startOffset: 12,
        endOffset: 64,
      },
    ]
    vi.mocked(api.createLoreEntity).mockResolvedValue(created)

    render(
      <RoutedCatalogue
        contentApi={api}
        loreApi={api}
        canEdit
        policies={[publicPolicy]}
        realmId="realm-1"
        sources={[source]}
        onOpenEvidence={vi.fn()}
      />,
    )

    await waitFor(() => expect(screen.getByRole('button', { name: 'Nueva ficha' })).toBeEnabled())
    fireEvent.click(screen.getByRole('button', { name: 'Nueva ficha' }))
    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Nara Vey' } })
    fireEvent.change(screen.getByLabelText('Descripción'), {
      target: { value: 'Nara Vey custodia el paso oriental.' },
    })
    const chunk = await screen.findByLabelText(/Nara Vey/)
    fireEvent.click(chunk)
    fireEvent.click(screen.getByRole('button', { name: 'Crear como propuesta' }))

    await waitFor(() => expect(api.createLoreEntity).toHaveBeenCalledWith('realm-1', {
      type: 'CHARACTER',
      displayName: 'Nara Vey',
      aliases: [],
      description: 'Nara Vey custodia el paso oriental.',
      accessPolicyId: 'policy-public',
      evidenceChunkIds: ['chunk-1'],
    }))
    expect(await screen.findByRole('button', { name: /Atlas público/ })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Registrar concepto' })).not.toBeInTheDocument()
  })

  it('promueve una propuesta a canon mediante una decisión explícita', async () => {
    const proposed = entity('entity-1', 'Nara Vey')
    const promoted = entity('entity-1', 'Nara Vey', 'CANON')
    const api = catalogueApi([proposed])
    vi.mocked(api.promoteLoreEntity).mockResolvedValue(promoted)

    render(
      <RoutedCatalogue
        contentApi={api}
        loreApi={api}
        canEdit
        policies={[publicPolicy]}
        realmId="realm-1"
        sources={[]}
        onOpenEvidence={vi.fn()}
      />,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'Promover a canon' }))
    await waitFor(() => expect(api.promoteLoreEntity).toHaveBeenCalledWith('realm-1', 'entity-1'))
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Promover a canon' })).not.toBeInTheDocument())
  })

  it('permite editar, promover y retirar fichas y relaciones visibles', async () => {
    const proposedRelation = { ...relation(), canonStatus: 'PROPOSED' as const }
    const promotedRelation = { ...proposedRelation, canonStatus: 'CANON' as const }
    const api = catalogueApi(
      [entity('entity-1', 'Nara Vey'), entity('entity-2', 'Lumbrevela')],
      [proposedRelation],
    )
    vi.mocked(api.promoteLoreRelation).mockResolvedValue(promotedRelation)
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)

    render(
      <RoutedCatalogue
        contentApi={api}
        loreApi={api}
        canEdit
        policies={[publicPolicy]}
        realmId="realm-1"
        sources={[]}
        onOpenEvidence={vi.fn()}
      />,
    )

    await screen.findByRole('heading', { name: 'Nara Vey' })
    fireEvent.click(screen.getByRole('link', { name: 'Relaciones' }))
    await screen.findByText('Nara vive en Lumbrevela.')
    fireEvent.click(screen.getByRole('button', { name: 'Editar' }))
    expect(screen.getByRole('heading', { name: 'Editar relación' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    fireEvent.click(screen.getByRole('button', { name: 'Promover a canon' }))
    await waitFor(() => expect(api.promoteLoreRelation).toHaveBeenCalledWith('realm-1', 'relation-1'))
    fireEvent.click(screen.getByRole('button', { name: 'Retirar' }))
    await waitFor(() => expect(api.deleteLoreRelation).toHaveBeenCalledWith('realm-1', 'relation-1'))

    fireEvent.click(screen.getByRole('link', { name: 'Fichas' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Editar' })[0])
    expect(screen.getByRole('heading', { name: 'Editar concepto' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Retirar' })[0])
    await waitFor(() => expect(api.deleteLoreEntity).toHaveBeenCalled())
    confirm.mockRestore()
  })
})
