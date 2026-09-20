import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, it, expect, vi } from 'vitest'
import { SourceJobsPanel } from './SourceJobsPanel'
import type { SourceJobView } from './model'
import { sourceDocument } from '../../test/contentApi'
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })
const job: SourceJobView = {
  id: 'job', documentId: 'doc', versionId: 'v2', versionNumber: 2, title: 'Crónica', originalFilename: 'cronica.md',
  accessPolicyId: 'policy', state: 'FAILED', attempts: 3, completedChunks: 2, totalChunks: 5,
  errorCode: 'FILE_UNAVAILABLE', noOp: false, nextAttemptAt: '2026-09-05T12:00:00Z', createdAt: '2026-09-05T12:00:00Z', history: [],
}
describe('SourceJobsPanel', () => {
  it('distingue un reemplazo fallido de la versión que sigue publicada', () => {
    render(<SourceJobsPanel jobs={[job]} sources={[{ ...sourceDocument, id: 'doc', versionId: 'v1' }]} busy={false} onRetry={vi.fn()} onRecover={vi.fn()} />)
    expect(screen.getByText('La versión 1 sigue publicada y disponible para consultas.')).toBeInTheDocument()
    expect(screen.getByText('Falta una copia íntegra del archivo. Cárgalo nuevamente.')).toBeInTheDocument()
  })
  it('recupera el mismo documento aunque aún no haya ninguna versión publicada', () => {
    const recover = vi.fn().mockResolvedValue(true), retry = vi.fn()
    render(<SourceJobsPanel jobs={[job]} sources={[]} busy={false} onRetry={retry} onRecover={recover} />)
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(retry).toHaveBeenCalledWith('job')
    const file = new File(['Nara no entregó 37 monedas.'], 'cronica.md')
    fireEvent.change(screen.getByLabelText('Reemplazar archivo de Crónica'), { target: { files: [file] } })
    expect(recover).toHaveBeenCalledWith('doc', 'policy', file)
  })
  it('no ofrece reintentar un trabajo cancelado y muestra el progreso del que sigue en curso', () => {
    const { rerender } = render(<SourceJobsPanel jobs={[{ ...job, state: 'CANCELLED' }]} sources={[]} busy={false} onRetry={vi.fn()} onRecover={vi.fn()} />)
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
    rerender(<SourceJobsPanel jobs={[{ ...job, state: 'RUNNING' }]} sources={[]} busy={false} onRetry={vi.fn()} onRecover={vi.fn()} />)
    expect(screen.getByRole('progressbar')).toHaveAttribute('value', '2')
  })

  it('permite reprocesar el mismo documento y cancelar la selección de archivo', () => {
    const recover = vi.fn().mockResolvedValue(true)
    render(<SourceJobsPanel jobs={[job]} sources={[]} busy={false} onRetry={vi.fn()} onRecover={recover} />)
    fireEvent.click(screen.getByRole('button', { name: 'Reprocesar' }))
    expect(recover).toHaveBeenCalledWith('doc', 'policy')
    fireEvent.change(screen.getByLabelText('Reemplazar archivo de Crónica'), { target: { files: [] } })
    expect(recover).toHaveBeenCalledOnce()
  })

  it('deshabilita la recuperación mientras otra operación está guardándose', () => {
    const retry = vi.fn(), recover = vi.fn()
    render(<SourceJobsPanel jobs={[job]} sources={[]} busy onRetry={retry} onRecover={recover} />)
    const retryButton = screen.getByRole('button', { name: 'Reintentar' })
    const reprocessButton = screen.getByRole('button', { name: 'Reprocesar' })
    expect(retryButton).toBeDisabled()
    expect(reprocessButton).toBeDisabled()
    expect(screen.getByLabelText('Reemplazar archivo de Crónica')).toBeDisabled()
    fireEvent.click(retryButton)
    fireEvent.click(reprocessButton)
    expect(retry).not.toHaveBeenCalled()
    expect(recover).not.toHaveBeenCalled()
  })

  it('muestra el historial y el próximo reintento sin ofrecer una recuperación manual en cola', () => {
    render(<SourceJobsPanel jobs={[{
      ...job, state: 'QUEUED', errorCode: 'MODEL_UNAVAILABLE',
      history: [{ state: 'RUNNING', attempt: 1, errorCode: null, createdAt: job.createdAt }],
    }]} sources={[]} busy={false} onRetry={vi.fn()} onRecover={vi.fn()} />)
    expect(screen.getByText(/Próximo intento:/)).toBeInTheDocument()
    expect(screen.getByText(/Procesando · intento 1/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  })

  it('presenta un error desconocido y progreso aún sin total sin romper el panel', () => {
    render(<SourceJobsPanel jobs={[{ ...job, state: 'RUNNING', totalChunks: 0, completedChunks: 0, errorCode: 'NEW_ERROR' }]}
      sources={[]} busy={false} onRetry={vi.fn()} onRecover={vi.fn()} />)
    expect(screen.getByText('La operación necesita revisión.')).toBeInTheDocument()
    expect(screen.getByRole('progressbar')).toHaveAttribute('max', '1')
  })
})
