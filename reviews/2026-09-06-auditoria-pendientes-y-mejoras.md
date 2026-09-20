# Auditoría de Codex of Realms — 6 de septiembre de 2026

Seguimiento: el punto 1 se cerró posteriormente con 108 tests y 78,09 % de cobertura de ramas. La [evidencia del cierre](2026-09-06-cierre-cobertura-y-aceptacion.md) detalla cambios y límites; los resultados de esta auditoría se conservan como estado inicial.

Las prioridades son completar la validación y mejorar la selección de evidencias. Los problemas encontrados afectan a la calidad de las respuestas, la sincronización del cliente y el coste de las consultas SQL. La autorización previa a la búsqueda, la revisión final de acceso y los trabajos persistentes de ingestión ya están implementados.

Análisis del árbol de trabajo del 6 de septiembre, incluidos los cambios sin commit. La revisión no modifica código. El grafo del 2 de septiembre se utilizó solo como orientación; los hallazgos se contrastaron con el código actual.

| Comprobación realizada | Resultado |
|---|---|
| Frontend: lint | Correcto |
| Frontend: TypeScript y build de producción | Correcto; JavaScript de 271,78 kB, 81,88 kB gzip |
| Frontend: tests con cobertura | 64 tests correctos; comando fallido por cobertura de ramas |
| Cobertura frontend | Ramas 73,65 % frente al 75 % exigido; líneas 81,51 %, funciones 76,45 %, sentencias 79,38 % |
| Backend: Maven verify | 148 entradas reportadas, 0 fallos de aserción y 3 errores de arranque de suites de integración por Docker no disponible |
| Reproducción aislada del selector | Confirmados el descarte a partir de 2.001 caracteres y la exclusión del segundo fragmento si el primero ocupa seis párrafos |
| Navegador, restauración y evaluación con modelos reales | No repetidos en esta auditoría; se revisaron sus pruebas y resultados guardados |

Docker tampoco respondió a su propia CLI: falta el pipe `dockerDesktopLinuxEngine`. Esto es una limitación del entorno durante esta revisión, no evidencia de un defecto del backend. Los informes antiguos presentes en `target` no se han sumado a los resultados de esta ejecución.

**1. Prioridad alta: la verificación local y CI no coinciden.**

`npm run test:coverage` falla actualmente: faltan 1,35 puntos de cobertura de ramas. El umbral está en [vite.config.ts](../frontend/vite.config.ts) y [CI](../.github/workflows/ci.yml) lo exige, mientras [verify-m8.2.ps1](../scripts/verify-m8.2.ps1) ejecuta `npm test`.

Acción: unificar el comando de aceptación y cubrir comportamientos relevantes de recuperación, polling, errores de red y clientes de contenido. `useRealmWorkspace.ts` queda en 42,42 % de ramas y contiene buena parte de esos recorridos. El criterio de cierre es que el mismo comando pase localmente y en CI, manteniendo el umbral.

**2. Prioridad alta: falta validar la cadena real de preguntas y respuestas.**

La [comparación guardada](2026-09-06-entrega-y-validacion.md) declara que ningún modelo cumple todos los umbrales. También identifica `gm-003`, rechazado por relevancia antes de llamar al modelo. Cambiar solo de modelo no resolvería ese rechazo en el experimento.

La evaluación utiliza [fuentes de referencia y un sustituto de búsqueda](../backend/src/test/java/dev/codexofrealms/qa/application/answering/LocalModelEvaluationIT.java), no el recorrido de embeddings y recuperación vectorial de producción. Es útil para aislar el selector, pero deja pendiente medir la cadena completa.

Acción: conservar esa evaluación aislada y añadir otra con ingestión real, bge-m3, PostgreSQL, usuarios autenticados y selección de párrafos de producción. Medir recuperación, rechazos de preguntas respondibles, cobertura de hechos, accesos y latencia; repetir en el hardware objetivo. Revisar cada rechazo antes de modificar umbrales.

**3. Prioridad media: el primer fragmento puede consumir todo el contexto.**

[LoreEvidenceService](../backend/src/main/java/dev/codexofrealms/lore/application/retrieval/LoreEvidenceService.java) expande los fragmentos en orden y termina al reunir seis párrafos. No reparte ese presupuesto entre fragmentos ni reordena los párrafos por su propia relevancia.

Reproducción sobre las clases actuales: primer fragmento con seis párrafos y segundo con un dato adicional; resultado de seis párrafos, ninguno del segundo fragmento. El comportamiento es confirmado; su impacto sobre la calidad del corpus real aún debe medirse.

Acción: evaluar un reparto inicial entre fragmentos/fuentes o una clasificación posterior de párrafos. Añadir un caso que necesite información de dos fuentes y comprobar que ambas llegan al selector.

**4. Prioridad media: una fuente puede estar lista y tener contenido inutilizable para las respuestas.**

[VisiblePassageService](../backend/src/main/java/dev/codexofrealms/content/application/evidence/VisiblePassageService.java) omite íntegramente los párrafos de más de 2.000 caracteres UTF-16. Es una restricción intencional, documentada y probada, pero la ingestión acepta esos textos y puede indexarlos correctamente. El usuario no recibe una advertencia específica en la subida.

Reproducción: un párrafo de 2.000 caracteres produce un candidato; uno de 2.001 produce cero.

Acción: informar al subir sobre párrafos excluidos y cobertura utilizable de la fuente. Si se amplía el soporte, definir una segmentación que conserve el contexto y los offsets exactos; evitar truncar sin criterio frases o negaciones.

**5. Prioridad media: el refresco de trabajos escala con todo el historial.**

[El listado](../backend/src/main/java/dev/codexofrealms/content/infrastructure/SourceJobJdbcRepository.java) no tiene paginación y [su mapper](../backend/src/main/java/dev/codexofrealms/content/infrastructure/SourceJobJdbcRepository.java) consulta los eventos por cada trabajo: 1 + N consultas del repositorio para N trabajos, antes de otras consultas del endpoint. El cliente repite el listado y las fuentes cada dos segundos mientras detecta trabajo pendiente.

[V8](../backend/src/main/resources/db/migration/V8__persist_source_jobs.sql) tampoco crea un índice por `source_job_event.job_id`. La clave foránea no lo añade automáticamente, según la [documentación de PostgreSQL](https://www.postgresql.org/docs/current/ddl-constraints.html).

Acción: separar trabajos activos e historial paginado, cargar eventos al desplegar un trabajo o agrupar su consulta, y añadir mediante nueva migración un índice `(job_id, id)`. Medir consultas y P95 con un historial representativo; no se ha medido latencia de base de datos en esta sesión.

**6. Prioridad media: lecturas repetidas del mismo original en cada pregunta.**

Cada fragmento procesado por [LoreEvidenceService](../backend/src/main/java/dev/codexofrealms/lore/application/retrieval/LoreEvidenceService.java) provoca [lectura y separación del archivo completo](../backend/src/main/java/dev/codexofrealms/content/application/evidence/VisiblePassageService.java). Si varios fragmentos pertenecen a una misma versión, se repite el trabajo; la validación final vuelve a leer los originales seleccionados.

Acción: agrupar por versión y reutilizar el texto y los párrafos dentro de la petición. Mantener una comprobación actual de autorización al final; la optimización no debe convertir permisos mutables en datos cacheados. Medir lecturas por pregunta y tiempo de CPU antes y después.

El catálogo tiene un patrón parecido: [toView](../backend/src/main/java/dev/codexofrealms/lore/infrastructure/LoreEntityJdbcRepository.java) consulta alias, evidencias y promociones por entidad. Conviene abordar paginación y consultas por lotes cuando crezca el corpus.

**7. Prioridad media: el cliente no descubre cambios externos cuando deja de consultar trabajos.**

[El polling](../frontend/src/app/workspace/useRealmWorkspace.ts) se detiene cuando no hay operaciones activas. Una nueva subida desde otra pestaña o por otro editor no lo reactiva. Los jugadores solo cargan las fuentes al montar el workspace. Esto puede dejar el listado desactualizado aunque el servidor ya sirva información nueva.

Acción: refrescar al recuperar el foco y tras reconexión, añadir un botón de actualización y valorar un refresco periódico moderado. Probar dos sesiones sobre el mismo realm. No hay evidencia de una fuga de permisos derivada de este comportamiento; es un problema de actualización del cliente.

**8. Prioridad media: renombrar una entidad deja antiguos nombres en sus relaciones.**

[saveEntity](../frontend/src/features/lore/useCatalogueWorkspace.ts) actualiza `entities`, pero no `relations`. Las [tarjetas de relaciones](../frontend/src/features/lore/relation/RelationList.tsx) muestran los campos `sourceEntityName` y `targetEntityName` guardados en estas últimas; el buscador también los usa.

Recorrido deducido del estado del cliente: renombrar una entidad ya relacionada y cambiar a la pestaña de relaciones dentro del Atlas. El nombre anterior se mantiene hasta recargar el catálogo. Este recorrido no se ejecutó en navegador durante la auditoría.

Acción: invalidar/recargar las relaciones tras editar una entidad o derivar los nombres de un mapa de entidades por ID. Comprobar visualización y búsqueda por el nombre nuevo.

**9. Prioridad media: las pruebas de navegador y recuperación no forman parte de CI.**

El workflow actual ejecuta backend, frontend y validación sintáctica de Compose, pero no el recorrido Playwright ni el contrato PowerShell de backup. Las comprobaciones de navegador existentes requieren su ejecución separada.

Acción: incorporar el entorno determinista de `compose.e2e.yaml` en un job aislado y conservar trazas de fallos. Añadir el contrato de backup; reservar la restauración completa para una comprobación programada o previa a entrega, según coste. [Playwright documenta la ejecución en CI](https://playwright.dev/docs/ci).

**10. Mejoras de producto y mantenimiento de menor prioridad.**

- Permitir crear otro universo cuando el usuario ya pertenece a uno: [App.tsx](../frontend/src/app/App.tsx) solo muestra el formulario con cero realms.
- Actualizar las capacidades del modelo al recuperar foco o pulsar «Comprobar»: [la carga actual](../frontend/src/app/App.tsx) se ejecuta una vez y puede mantener un aviso obsoleto tras iniciar Ollama.
- Reconciliar ROADMAP, threat model y metadatos de versión con la entrega extractiva actual. El roadmap aún conserva pendientes y cifras históricas que no representan esta entrega; `info.application.milestone` sigue en M8.1.
- Valorar búsqueda y paginación de fuentes, enlaces directos a fichas y un aviso de cambios sin guardar en el Atlas. Son propuestas de producto, no bloqueos actuales.

El orden propuesto es: cerrar cobertura y coherencia de aceptación; corregir y evaluar la selección de párrafos; reducir consultas y lecturas repetidas; resolver actualización del cliente y ampliar CI. La compilación observada no señala una urgencia por reducir el bundle. Los cambios de arquitectura o índices vectoriales necesitan una medición específica antes de decidirse.

La reproducción técnica quedó en EvidenceAudit.java (`backend/target/audit/EvidenceAudit.java`, referencia histórica local), bajo artefactos locales de build. No modifica ni utiliza datos de la aplicación.
