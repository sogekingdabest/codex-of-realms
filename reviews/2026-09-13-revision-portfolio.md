# Revisión de arquitectura, producto y preparación para currículum

Revisión del árbol de trabajo local del 13 de septiembre de 2026, incluidos los cambios sin confirmar. Los hallazgos describen ese estado; esta revisión no los corrige.

## Opinión

El proyecto permite explicar decisiones de backend Java y full stack: autorización, transacciones, persistencia, recuperación de trabajos, límites entre módulos y evaluación de IA. La siguiente prioridad es completar los recorridos de usuario y preparar una demo reproducible.

La utilidad potencial es concreta: consultar notas de una campaña y compartir conocimiento sin revelar información reservada a la dirección o a otros jugadores. Falta comprobar esa utilidad con una campaña real y jugadores externos.

## Verificación realizada

| Comprobación | Resultado observado en esta revisión |
|---|---|
| Backend, `mvnw.cmd --batch-mode --no-transfer-progress verify` | BUILD SUCCESS; 185 pruebas, cero fallos, errores u omisiones. Incluye arquitectura y PostgreSQL/pgvector con Testcontainers. |
| Frontend, `npm run verify` | Aprobado: lint, 108 pruebas, umbrales de cobertura y compilación. Cobertura: 87,55 % líneas y 78,29 % ramas. |
| Construcción y arranque de `compose.e2e.yaml` | Imágenes construidas y servicios arrancados; su espera no garantiza que Keycloak ya acepte conexiones. |
| Primera ejecución de `npm run test:e2e` | Falló esperando el enlace Register durante el arranque de Keycloak. |
| Segunda ejecución, con Keycloak iniciado | Falló al introducir la segunda invitación. La captura muestra el correo vacío y validación HTML de campo obligatorio. |
| Inspección visual | Inicio de sesión, Archivo y consultas, administración y estado vacío del Atlas en navegador de escritorio. |
| Modelos reales y restauración de backups | No repetidos. Se leyeron los informes existentes; sus resultados no se presentan como una nueva ejecución. |

En las pruebas de backend aparecieron errores del trabajador programado cuando una base de Testcontainers se cerró; no fallaron las aserciones. Conviene ordenar la terminación de esos contextos para que los logs no oculten fallos futuros.

El grafo de graphify se consultó como orientación con los términos `architecture module authorization ingestion retrieval answer`. Su fecha era 2 de septiembre, por lo que se contrastó con el código actual. No se considera una certificación de la arquitectura presente.

## Lo que está bien construido

- **Monolito modular adecuado al alcance.** `realm`, `content`, `lore`, `qa` y `runtime` tienen responsabilidades reconocibles. Modulith comprueba los límites y ArchUnit impide dependencias de aplicación a adaptadores y de dominio a capas externas. Las fronteras son comprobables, no solo carpetas.
- **Puertos útiles.** Repositorios, almacenamiento de originales y modelos tienen interfaces en puntos de sustitución reales. La división de persistencia de `realm` por capacidad mejora la claridad sin multiplicar procesos.
- **Autorización dentro del recorrido de datos.** La consulta de recuperación filtra universo, membresía, políticas y versiones activas antes de ordenar evidencia. Se revalida la visibilidad antes de devolver una respuesta.
- **IA con autoridad limitada.** El modelo selecciona identificadores y el servidor copia pasajes originales. Esto protege la literalidad y procedencia; no demuestra que el pasaje responda bien a la pregunta.
- **Ingestión con recuperación.** Idempotencia, estados persistidos, leases, reintentos y activación separada del trabajo de embeddings resuelven problemas reales. Es una buena pieza para explicar decisiones de concurrencia en una entrevista.
- **Pruebas y operación.** Migraciones, pruebas con una base real, CI, métricas y procedimientos de recuperación aportan sustancia. Separar pruebas deterministas de evaluación real de modelos es una decisión acertada.

No considero necesario migrar a microservicios, añadir otro almacenamiento o hacer una refactorización general de capas para que sea un buen proyecto de currículum.

## Hallazgos prioritarios

### 1. La aceptación completa de navegador no pasa actualmente

**Arranque:** en `compose.e2e.yaml:15`, Keycloak tiene `KC_HEALTH_ENABLED`, pero no un `healthcheck` de Compose. El backend depende de él con `service_started` en la línea 66. El procedimiento documentado puede terminar su `--wait` antes de que el login esté disponible. Se reprodujo con un fallo esperando Register; los logs posteriores mostraron que Keycloak aún estaba completando su importación y arranque.

**Invitaciones consecutivas:** `useInvitations.ts:51` publica la primera invitación antes de esperar la recarga de miembros. `InvitationControls.tsx:21` ejecuta `formElement.reset()` cuando termina toda esa operación, mientras el campo de correo continúa editable. Si ya se ha escrito el siguiente correo, el reset lo borra. La segunda ejecución falló exactamente con el campo vacío. La traza contiene un único POST de invitación, con 201, seguido de un GET de miembros con 200; no hay un segundo POST.

Corregir el ciclo del formulario para no borrar ediciones posteriores, y hacer que la prueba espere la finalización explícita de cada envío. Añadir una comprobación real de disponibilidad de identidad al arranque. Son los primeros cambios que haría antes de presentar una demo.

### 2. La biblioteca no permite leer una fuente directamente

`SourcesPanel.tsx:57` presenta título, estado y acciones de mantenimiento, pero ninguna acción para abrir el documento. `useRealmWorkspace.ts:194` obtiene el contenido al inspeccionar citas o evidencia del catálogo.

Esto limita la utilidad básica: un jugador que sabe qué crónica necesita no puede simplemente abrirla desde Fuentes. También deja pocas alternativas cuando la IA se abstiene. Daría prioridad a lectura completa y búsqueda de fuentes, reutilizando la autorización y las APIs existentes.

### 3. La creación de universos desaparece después del primero

`App.tsx:89` muestra el formulario únicamente cuando `me.realms.length === 0`. Quien ya pertenece a una campaña tampoco puede crear la suya desde la interfaz. Mantendría una acción «Crear universo» junto al selector y trataría los errores de creación dentro del formulario, sin sustituir toda la aplicación por el error de carga inicial.

### 4. El Atlas y las respuestas tienen contratos distintos

`QuestionAnsweringService` utiliza `LoreSearch` y `LoreEvidence`. La recuperación en `PgVectorLoreRetriever` consulta documentos y fragmentos, no fichas o relaciones del Atlas. Promover una ficha a canon no hace que sus datos se utilicen al responder.

Es una decisión posible de alcance, pero el producto debe explicarla. De otro modo, el usuario puede esperar que corregir el canon corrija las respuestas. Antes de integrar nuevas fuentes de conocimiento, definiría qué significa canon para las consultas y cómo se muestra su procedencia. Esta decisión no exige GraphRAG.

### 5. Historial de trabajos sin límite y con N+1

`SourceJobJdbcRepository.java:87` carga todos los trabajos del universo. Su mapper, en la línea 330, ejecuta otra consulta para obtener el historial de cada trabajo. Con N operaciones se producen 1+N consultas para ese listado; la interfaz repite el listado cada dos segundos mientras hay trabajo pendiente.

No se midió una degradación con un corpus grande, pero el patrón de crecimiento está en el código. Paginar operaciones antiguas, cargar el detalle al abrirlo y agrupar consultas evitaría que el uso prolongado encarezca el panel. La UI también muestra todos los trabajos antes del listado de fuentes.

### 6. Diálogo de evidencia con accesibilidad incompleta

`EvidenceDialog.tsx` usa `<dialog open>` y un aspecto de superposición, sin `showModal()`, gestión explícita del foco o cierre por Escape. El atributo open por sí solo abre un diálogo no modal. Revisaría navegación de teclado, confinamiento y devolución del foco. Hallazgo por inspección de código; no se completó una auditoría de accesibilidad ni una prueba del diálogo con lector de pantalla.

### 7. La documentación pública necesita consolidarse

El README contiene estados históricos y cifras sucesivas. `docs/operations/DEMO.md:30` todavía indica descargar `qwen3:4b`, mientras la configuración y el README actuales seleccionan `qwen3.5:4b`. `docs/product/MVP_SCOPE.md:69` excluye procesamiento asíncrono, ya implementado. El informe de portfolio M7 sigue describiendo el validador generativo histórico.

Conservaría ese historial claramente fechado, pero ofrecería una única entrada vigente: qué hace, captura o vídeo, cómo probarlo, requisitos, limitaciones actuales y tres decisiones técnicas. Quien evalúa el currículum no debería reconstruir la evolución del proyecto para entenderlo.

## Diseño y experiencia

La paleta oscura, los acentos verdes y dorados, la tipografía serif y los paneles forman una identidad coherente con un archivo de fantasía. En escritorio la presentación resulta cuidada.

La jerarquía reserva demasiado espacio a presentación: en la ventana observada, aproximadamente los primeros 430 píxeles preceden al contenido de trabajo. En el Atlas se añade otra cabecera antes de filtros y fichas. Reduciría esa zona tras entrar en una campaña.

Separaría tareas de consulta de administración: lectura y búsqueda accesibles inmediatamente; alta, reemplazo y procesamiento de fuentes bajo acciones específicas. El historial completo de operaciones no debería ocupar el centro de la biblioteca. Las relaciones del Atlas se presentan como tarjetas de texto; poder ir de una relación a sus fichas sería más útil que añadir una visualización compleja inicialmente.

También revisaría el lenguaje: «realm», «runtime», nombres de modelos, offsets y frases como «promoción humana» tienen utilidad técnica, pero pueden desplazarse a detalles o ayudas. La pregunta principal de un jugador es dónde encontrar información y quién puede verla.

No se verificó visualmente la versión móvil ni se probó la lectura con una biblioteca grande; no se declara resuelto ese apartado.

## Calidad de IA y utilidad

El informe `2026-09-13-contexto-y-omisiones.md` declara ACCEPTANCE_BLOCKED y conserva desactivadas las opciones experimentales. En v5, la referencia obtuvo 90,48 % de hechos y 93,75 % de citas satisfactorias; la candidata subió hechos a 95,24 % pero mantuvo 93,75 % de citas. El control léxico rechazó selecciones correctas por variaciones de palabras. Son mediciones existentes, no resultados nuevos de esta revisión.

Por tanto, no presentaría el proyecto como un asistente que comprende y responde correctamente cualquier cuestión del canon. Sí como un archivo con control de acceso y selección de evidencia literal, con calidad medida y límites conocidos. Es una propuesta valiosa si ahorra tiempo en campañas reales.

La siguiente validación útil sería observar a una persona que dirige partidas y dos jugadores usando sus propias notas: tiempo para encontrar una respuesta, consultas abandonadas, falsos rechazos, omisiones y mantenimiento requerido. Compararía ese esfuerzo con cómo buscan hoy. Una biblioteca consultable seguiría aportando valor incluso cuando el selector no responda.

## Orden recomendado

1. Corregir arranque de aceptación e invitaciones consecutivas; conseguir el flujo completo verde desde un entorno limpio.
2. Abrir y buscar fuentes, crear más universos y mejorar navegación y foco.
3. Preparar una demostración breve con datos originales ya cargados y una entrada documental vigente.
4. Validar utilidad con una campaña real y decidir el contrato entre Atlas y consultas.
5. Mejorar recuperación y selección con preguntas independientes, manteniendo los criterios de evaluación.

Para el currículum destacaría tres aportaciones defendibles: monolito modular verificado, autorización antes de recuperación y trabajos idempotentes recuperables. Acompañaría cada una con una demostración concreta y explicaría con honestidad las limitaciones de IA.
