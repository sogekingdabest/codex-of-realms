# Codex of Realms — revisión de arquitectura y producto, 5 de septiembre de 2026

La recomendación es conservar el monolito modular y dedicar la siguiente etapa a fiabilidad, coherencia del canon y experiencia de uso. Spring Boot, React y PostgreSQL con pgvector encajan con una aplicación de campañas que combina documentación, permisos y consultas con evidencia. Para el portfolio conviene mostrar esos recorridos funcionando y explicar sus límites.

La revisión corresponde al directorio de trabajo del 5 de septiembre, incluidos los cambios sin confirmar. Se consultó el grafo existente, fechado el 2 de septiembre, y se contrastaron sus indicaciones con los archivos actuales. No se han modificado funcionalidades ni configuraciones de la aplicación. Este documento recoge propuestas; no cambia el roadmap acordado.

**Qué conservar y qué evolucionar**

| Área | Decisión propuesta | Motivo |
|---|---|---|
| Backend | Mantener Java 21, Spring Boot y el monolito modular | Los módulos `realm`, `content`, `lore`, `qa` y `runtime` representan capacidades reconocibles. Modulith y ArchUnit ya comprueban límites. |
| Persistencia | Mantener PostgreSQL, JDBC, Flyway y pgvector | El SQL explícito permite revisar permisos y recuperación conjuntamente. Introducir otro almacén duplicaría operaciones y sincronización. |
| Frontend | Mantener React, TypeScript y Vite; incorporar gestión de consultas cuando se corrija la sincronización | Ya existe separación por funcionalidades. El problema concreto es coordinar datos remotos y sus cambios. |
| Identidad | Mantener OIDC y Keycloak; completar verificación de email y configuración de despliegue | Los roles de campaña siguen perteneciendo a la aplicación. El proveedor resuelve autenticación y verificación de identidad. |
| IA | Mantener puertos para modelos y Ollama como opción local | Encaja con el hardware objetivo. Para una demo remota, el proveedor y el presupuesto de inferencia deben ser decisiones explícitas. |
| Operación | Mantener Compose, backups, restauración y métricas | Ya aportan evidencia práctica de mantenibilidad. La prioridad es cerrar la experiencia completa del navegador. |

No encuentro una necesidad actual que justifique microservicios, Kubernetes, Kafka, Neo4j o migrar el frontend a Next.js. Consideraría esas incorporaciones únicamente ante una necesidad medida. Visualizar las relaciones existentes tampoco exige cambiar de base de datos.

**Orden de trabajo propuesto**

| Prioridad | Entrega | Esfuerzo relativo | Evidencia de aceptación |
|---|---|---|---|
| P0 | Invitaciones vinculadas a identidad verificada | Pequeño–medio | Un email no verificado nunca activa una membresía. |
| P1 | Respuestas y citas con límites honestos | Medio | Casos de negación, cifras y atribuciones erróneas detectados o rechazados; evaluación humana separada. |
| P1 | Fuentes recuperables y trabajos persistentes | Medio–alto | Reiniciar durante una subida permite continuar o reintentar sin duplicar versiones. |
| P1 | Ciclo coherente entre fuentes, Atlas y consultas | Alto, divisible | Actualizar una fuente identifica canon afectado; cada respuesta conserva procedencia y permisos. |
| P1 | Prueba completa de navegador con varias identidades | Medio | Owner y jugadores recorren invitación, subida, consulta, cita y revocación. |
| P2 | Navegación, sincronización y cierre de operaciones de UI | Medio | Enlaces directos, segundo universo, actualización de fuentes y relaciones sin datos obsoletos. |
| P2 | Lecturas paginadas y conflictos de edición | Medio | Coste de consultas acotado y ningún cambio concurrente se pierde silenciosamente. |
| P2 | Demo publicable y documentación coherente | Medio | Un tercero puede probarla y distinguir resultados medidos, limitaciones y funciones disponibles. |

Los esfuerzos son comparativos, no estimaciones de calendario.

**1. Corregir la aceptación de invitaciones por email**

El [resolutor de identidad](../backend/src/main/java/dev/codexofrealms/CurrentUserArgumentResolver.java) transmite `email` sin leer `email_verified`. [AuthenticatedUserService](../backend/src/main/java/dev/codexofrealms/realm/application/identity/AuthenticatedUserService.java) acepta invitaciones en cada sincronización y el repositorio crea membresías por coincidencia de correo. La configuración importable permite registro y no declara la verificación de email como requisito.

El backend, por tanto, no garantiza que quien reclama una invitación controle el correo. Con un proveedor que permita registrar correos sin verificar, podría reclamarse una invitación pendiente dirigida a otra persona. Es un hallazgo del flujo de código; no se realizó una suplantación en un servidor activo.

Propongo conservar `issuer + subject` como identidad, transmitir una señal de verificación confiable y exigirla antes de aceptar una invitación por email. Activar verificación en Keycloak, configurar entrega de correo y probar tanto el claim ausente como `false`. Como evolución de producto, añadir aceptación explícita y caducidad. [OpenID Connect distingue email de email verificado](https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims).

**2. Ajustar la promesa de «respuesta verificada» y evaluar contradicciones**

[AnswerValidator](../backend/src/main/java/dev/codexofrealms/qa/application/answering/AnswerValidator.java) comprueba referencias y cobertura léxica. [TextTerms](../backend/src/main/java/dev/codexofrealms/qa/application/answering/TextTerms.java) descarta palabras cortas, incluida «no». En una comprobación aislada contra las clases actuales, la fuente «Nara no pertenece a la Cofradia del Bronce.» y la afirmación contraria «Nara pertenece a la Cofradia del Bronce.» obtienen cobertura 1,0 y resultado `ANSWERED`.

Esto demuestra una limitación del validador, no que el modelo produzca habitualmente esa respuesta. Tener citas existentes y autorizadas no demuestra que sostengan semánticamente todas las afirmaciones. Subir el umbral léxico tampoco soluciona el ejemplo.

Primero incorporaría pruebas de negaciones, sujeto/objeto intercambiados, fechas, cantidades y paráfrasis. Separaría las métricas de validez de citas, hechos correctos, rechazo y permisos. Para una primera entrega fiable, ofrecería fragmentos exactos como alternativa cuando la síntesis no sea suficientemente fiable, y un texto de UI como «Respuesta con fuentes». Cualquier verificador semántico adicional debe evaluarse en calidad y latencia; otro modelo tampoco convierte la comprobación en una garantía absoluta.

La comprobación encontró además que el detector de instrucciones reconoce `actua como`, pero no `actúa como`: [EvidenceGate](../backend/src/main/java/dev/codexofrealms/qa/application/answering/EvidenceGate.java) sustituye marcas diacríticas por espacios. Corregir la normalización es concreto y acotado. La lista de patrones debe seguir siendo una defensa complementaria; la autorización previa a recuperación es la protección fundamental de contenido restringido.

**3. Hacer recuperable el procesamiento de fuentes**

La subida en [SourceIngestionService](../backend/src/main/java/dev/codexofrealms/content/application/ingestion/SourceIngestionService.java) escribe el archivo, genera fragmentos, calcula todos los embeddings y activa la versión dentro de la petición HTTP. Ya están bien separados los tramos transaccionales del trabajo con el modelo; conservaría esa separación.

El problema visible es que [listAccessible](../backend/src/main/java/dev/codexofrealms/content/infrastructure/SourceJdbcRepository.java) devuelve únicamente versiones activas y `READY`. La UI tiene una etiqueta «Fallida», pero el listado no entrega los fallos de nuevas subidas; el POST fallido tampoco añade la fuente al estado del frontend. Un reinicio durante `PROCESSING` carece de una recuperación persistente identificable en el código revisado.

Propuesta: registrar archivo y trabajo, responder `202` con identificador y consultar progreso. Un worker en el mismo backend puede reclamar trabajos desde PostgreSQL, con intentos, plazo de ejecución, recuperación tras reinicio e idempotencia. Limitar inicialmente la concurrencia según el hardware. La persistencia del trabajo, la activación de la versión y la limpieza de archivos deben tener una estrategia explícita de reintento. Incorporar un listado de administración con pendientes y fallidos, separado del contenido visible para jugadores.

También hay un desajuste de tiempos: [Nginx](../frontend/nginx.conf) no configura `proxy_read_timeout`; su [valor predeterminado es 60 segundos sin recibir datos del upstream](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_read_timeout). El backend permite una lectura de IA de dos minutos. Una petición silenciosa larga puede terminar en 504 mientras el backend sigue trabajando. Conviene alinear límites, probar arranque en frío y mostrar estados reales. No mostraría tokens de respuesta antes de completar su validación.

**4. Unir el ciclo de vida del canon con sus fuentes y consultas**

El recuperador [PgVectorLoreRetriever](../backend/src/main/java/dev/codexofrealms/lore/infrastructure/retrieval/PgVectorLoreRetriever.java) consulta `lore_chunk` y versiones de documentos. No incorpora las entidades y relaciones promovidas del Atlas. Crear un hecho manual y promoverlo no añade ese hecho a la recuperación. Es una separación funcional que conviene decidir expresamente.

Además, al reemplazar una fuente, la versión anterior se retira, mientras las fichas conservan referencias a esa versión. [findAccessibleVersion](../backend/src/main/java/dev/codexofrealms/content/infrastructure/SourceJdbcRepository.java) exige que esté activa y `READY`, por lo que una evidencia histórica del catálogo puede dejar de abrirse. El comportamiento deriva del código; no se reprodujo el flujo en base de datos durante esta revisión.

Propongo definir tres piezas: documento como evidencia, afirmación humana publicada como canon, y borrador de cambio como propuesta. Si el Atlas debe alimentar las respuestas, hacerlo mediante un contrato de evidencia explícito que distinga citas a documentos y revisiones de canon, siempre con permisos. No indexar propuestas como hechos publicados.

Añadiría revisiones de fichas y «necesita revisión» cuando cambie una fuente. La lectura histórica requiere reglas propias: no basta con eliminar `v.active`, porque una sustitución puede haber restringido acceso. El historial debe respetar revocaciones y la política vigente que se defina. Una edición debería poder mantener publicada la revisión anterior hasta aprobar la nueva; actualmente se sobrescribe la ficha y se devuelve a `PROPOSED`.

**5. Simplificar el estado remoto y cerrar operaciones del frontend**

La organización por funcionalidades es adecuada. El problema está en el estado remoto manual de [useCatalogueWorkspace](../frontend/src/features/lore/useCatalogueWorkspace.ts): editar una entidad actualiza `entities`, pero las relaciones cargadas conservan sus nombres anteriores hasta una recarga. La disponibilidad de modelos se consulta una sola vez al montar la app.

TanStack Query sería una incorporación justificada para invalidar entidades, relaciones, fuentes y capacidades tras cambios. Sus [mecanismos de invalidación](https://tanstack.com/query/latest/docs/framework/react/guides/query-invalidation) permiten refrescar consultas relacionadas. Usar claves con identidad y universo, vaciar la caché al salir y refrescar permisos; la caché nunca debe ser la autoridad de acceso. Mantener los borradores de formularios como estado local.

Añadir rutas para universo, ficha y documento, filtros conservados en URL y navegación atrás/adelante. Ahora [App](../frontend/src/app/App.tsx) solo muestra crear universo cuando no existe ninguno, y el selector no ofrece crear un segundo. El backend tiene reemplazo y reprocesamiento de fuentes, pero [ContentApi](../frontend/src/features/content/api.ts) no los expone. Completar esas operaciones aporta más utilidad inmediata que ampliar formatos de importación.

**6. Acotar las lecturas y proteger las ediciones simultáneas**

[LoreEntityJdbcRepository](../backend/src/main/java/dev/codexofrealms/lore/infrastructure/LoreEntityJdbcRepository.java) ejecuta tres lecturas adicionales por entidad: alias, evidencia e historial de promociones. Listar N entidades supone 1 + 3N consultas en ese método, además de la autorización. El listado tampoco pagina, y el frontend carga entidades y relaciones completas.

Propuesta: páginas acotadas con filtros de servidor, DTO resumido de listado y detalle bajo demanda; cargar colecciones por lotes cuando se necesiten. Medir consultas y latencia con un catálogo mayor. La búsqueda vectorial exacta es razonable en el volumen actual; no añadiría HNSW por defecto antes de medir su efecto en rendimiento y recuperación con filtros de acceso.

Las actualizaciones de entidades y relaciones tampoco llevan una revisión esperada. Introducir un contador de revisión o `ETag`/`If-Match`, devolver conflicto y permitir comparar cambios. Es especialmente útil con varios editores y al promover exactamente la revisión que una persona ha revisado.

**7. Probar la experiencia que va a evaluar otra persona**

La CI ya verifica backend, frontend y sintaxis de Compose. El [workflow](../.github/workflows/ci.yml) no ejecuta un recorrido de navegador completo. Los tests de componentes y la integración de PostgreSQL cubren capas útiles, pero no detectan por sí solos problemas entre login real, proxy, navegación y estados de pantalla.

Añadiría Playwright con owner y dos jugadores, autenticación real y [contextos de navegador separados](https://playwright.dev/docs/auth#testing-multiple-roles-together). Cubrir: crear campaña, aceptar invitación verificada, subir fuente, conceder spoiler a un jugador, consultar con las tres identidades, abrir evidencia, revocar y volver a consultar. Mantener IA determinista en esta suite y una evaluación de modelos reales separada. Añadir casos de fuente fallida y petición lenta por el proxy.

**8. Preparar una demo accesible y resultados defendibles**

Conservaría la ejecución local como opción, pero decidiría cómo probará la app una persona sin GPU. Una demo desplegada puede usar inferencia con presupuesto limitado y corpus original precargado; otra opción es un modo de demostración con respuestas preparadas claramente identificado, acompañado del flujo real reproducible. No presentar respuestas preparadas como generación en vivo.

El Compose actual usa `start-dev`, hosts localhost y publica puertos de infraestructura. Para una demo en Internet hace falta una configuración específica de HTTPS, orígenes, URLs, puertos internos y límites de uso de IA. Keycloak documenta [configuración de producción](https://www.keycloak.org/server/configuration-production); no requiere asumir que este proyecto necesite un clúster.

Actualizar README y ROADMAP para que coincidan: el primero afirma promoción revisada del modelo, mientras M5.1 conserva la comparación como pendiente. El informe local de comparación final también pide revisión manual antes de promover. Publicaría un informe curado con commit, corpus, hardware, repeticiones, revisión humana y límites. Un buen vídeo de tres minutos debería mostrar una consulta, su cita y cómo cambia la información visible entre jugadores.

**Funcionalidades siguientes, después de corregir los puntos anteriores**

| Funcionalidad | Valor para la campaña | Alcance inicial |
|---|---|---|
| Vista previa como jugador | Comprobar qué puede conocer cada persona | Aplicar permisos de la identidad simulada en backend y señalar siempre la simulación. |
| Historial del canon y fuentes afectadas | Entender qué cambió y qué afirmaciones deben revisarse | Revisiones y aviso de evidencia sustituida; mantener autorización histórica. |
| Búsqueda híbrida | Encontrar nombres inventados, alias y frases exactas | Combinar búsqueda textual y vectorial dentro del conjunto autorizado; evaluar contra un corpus reservado. [pgvector contempla esta combinación](https://github.com/pgvector/pgvector#hybrid-search). |
| Ficha navegable y mapa de relaciones | Explorar personajes, lugares y conexiones | Reutilizar relaciones y PostgreSQL; añadir visualización al frontend. |
| Notas de sesión con propuestas revisables | Convertir lo ocurrido en campaña en conocimiento mantenido | Guardar notas como fuentes y aprobar cambios manualmente antes de publicarlos. |

Dejaría PDF/OCR, extracción automática extensa y análisis de contradicciones entre todo el corpus para cuando el ciclo de Markdown/TXT, revisión y publicación resulte cómodo. El primer criterio de ampliación debería ser una necesidad observada usando una campaña real.

**Verificación realizada y límites**

- Frontend: lint correcto, 60 tests en 11 archivos correctos y build de producción correcto.
- Backend: la ejecución actual registró 124 casos, con 123 correctos y un error al iniciar `RealmAuthorizationIntegrationTest` por ausencia de Docker. Incluye verificación de módulos y reglas internas. `mvn verify` no terminó correctamente; no se considera validada la integración en esta sesión.
- Se comprobó que el daemon Docker no está disponible, también fuera del sandbox. No se arrancó ni alteró la pila activa.
- Comprobación aislada del validador: contradicción aceptada como `ANSWERED`, cobertura 1,0; diferencia entre `actua como` y `actúa como` reproducida. El pequeño programa queda en `backend/target/review-probes/ReviewProbe.java`, fuera del código de la aplicación.
- No se ejecutaron modelos reales, una prueba visual del navegador ni mediciones nuevas de carga. Los hallazgos de SQL, UI, invitaciones y versiones se basan en lectura del código y configuración; sus reproducciones completas se proponen como criterios de aceptación.

La siguiente entrega que recomiendo abordar reúne invitaciones verificadas, los casos adversariales del validador y la recuperación visible de fuentes fallidas. Después, cerrar el flujo de navegador con tres identidades y el vínculo entre fuentes y canon proporciona una base especialmente sólida para presentar el proyecto.
