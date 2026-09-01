export function ErrorToast({ message, onClose }: Readonly<{ message: string; onClose: () => void }>) {
  return <div className="error-toast" role="alert"><strong>No se pudo completar la operación.</strong><span>{message}</span><button type="button" aria-label="Cerrar aviso" onClick={onClose}>×</button></div>
}
