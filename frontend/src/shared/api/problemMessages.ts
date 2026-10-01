/**
 * Spanish copy for the stable `code` of the API's Problem Details.
 * The server's `detail` is diagnostic English text and is never shown to people.
 */
const messagesByCode: Record<string, string> = {
  'request.invalid': 'La petición no tiene un formato válido. Revisa los datos e inténtalo de nuevo.',

  'realm.unavailable': 'Este universo no está disponible o ya no tienes acceso a él.',
  'membership.not_found': 'Esa persona ya no forma parte del universo.',
  'membership.target_user_unavailable': 'No se encontró a esa persona en el universo.',
  'membership.last_owner': 'El universo necesita al menos un propietario activo.',
  'invitation.not_found': 'La invitación ya no existe o ha sido revocada.',
  'invitation.invalid_role': 'Solo se puede invitar con el rol de editor o de jugador.',
  'invitation.pending_exists': 'Ya hay una invitación pendiente para ese correo.',
  'invitation.already_member': 'Esa persona ya es miembro del universo.',
  'access_policy.unavailable': 'Esa visibilidad ya no está disponible.',
  'access_policy.grantee_unavailable': 'Esa persona ya no está disponible para recibir el spoiler.',
  'access_policy.grants_unsupported': 'Solo se pueden revelar a personas concretas las visibilidades de tipo spoiler.',
  'access_policy.invalid_grantee': 'Los spoilers solo se pueden revelar a jugadores activos del universo.',
  'access_policy.name_exhausted': 'No se pudo generar un nombre único para la visibilidad. Prueba con otro nombre.',

  'source.unavailable': 'Esta fuente ya no está disponible o no tienes acceso a ella.',
  'source.invalid': 'No se pudo aceptar la fuente. Usa un archivo Markdown o TXT en UTF-8, con texto y un título de hasta 200 caracteres.',
  'source.upload_too_large': 'El archivo supera el tamaño máximo permitido para una fuente.',
  'source.embedding_unavailable': 'El modelo de embeddings no está disponible. Revisa el runtime local e inténtalo de nuevo.',
  'source.upload_in_progress': 'La copia del archivo original sigue en curso. Espera unos segundos y vuelve a enviarlo.',
  'source.file_unavailable': 'Falta el archivo original de esta fuente. Vuelve a cargarlo.',
  'source.idempotency_conflict': 'Este envío coincide con otro anterior distinto. Vuelve a seleccionar el archivo e inténtalo de nuevo.',
  'source.job_not_retryable': 'Esta operación ya no se puede reintentar.',
  'source.pipeline_changed': 'La configuración de procesamiento ha cambiado. Reprocesa la fuente para crear una nueva versión.',
  'source_evidence.unavailable': 'Algún fragmento seleccionado ya no está publicado o no coincide con la visibilidad elegida.',
  'source_evidence.invalid': 'Puedes vincular como máximo 20 fragmentos distintos.',

  'lore_entity.unavailable': 'Esta ficha ya no está disponible o no tienes acceso a ella.',
  'lore_entity.active_relations': 'Retira primero las relaciones activas de esta ficha.',
  'lore_relation.unavailable': 'Esta relación ya no está disponible o no tienes acceso a ella.',
  'lore_relation.endpoint_unavailable': 'Alguna de las fichas de la relación ya no está disponible.',
  'lore_relation.duplicate': 'Ya existe una relación activa igual entre esas fichas.',

  'network.unavailable': 'No se pudo conectar con el servidor. Comprueba tu conexión e inténtalo de nuevo.',
}

const messagesByStatus: Record<number, string> = {
  400: 'La petición no es válida. Revisa los datos e inténtalo de nuevo.',
  401: 'Tu sesión ha caducado. Vuelve a iniciar sesión.',
  403: 'No tienes permiso para realizar esta acción.',
  404: 'El elemento solicitado ya no está disponible o no tienes acceso a él.',
  409: 'La operación entra en conflicto con un cambio reciente. Recarga e inténtalo de nuevo.',
  413: 'El archivo supera el tamaño máximo permitido.',
  429: 'Demasiadas peticiones seguidas. Espera un momento e inténtalo de nuevo.',
  503: 'Un servicio necesario no está disponible. Inténtalo de nuevo en unos minutos.',
}

export function problemMessage(code: string | null, status: number): string {
  if (code && messagesByCode[code]) return messagesByCode[code]
  if (messagesByStatus[status]) return messagesByStatus[status]
  if (status >= 500) return 'El servidor no pudo completar la operación. Inténtalo de nuevo en unos minutos.'
  return `No se pudo completar la operación (HTTP ${status}).`
}
