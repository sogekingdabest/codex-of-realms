# Mejoras de producto tras la revisión

Esta entrega resuelve los primeros puntos de [la revisión del 13 de septiembre](2026-09-13-revision-portfolio.md): arranque de la demo, búsqueda y lectura en la biblioteca, navegación, accesibilidad y documentación.

## Cambios

- **Arranque e invitaciones:** el entorno de aceptación espera la salud de Keycloak. El formulario de invitación conserva las ediciones realizadas mientras termina un envío anterior y evita el doble envío.
- **Biblioteca:** búsqueda local por título o archivo, insensible a mayúsculas y tildes; lectura completa mediante la API autorizada existente; alternancia entre fragmento citado y documento. Se cancelan lecturas al cambiar de universo y se ignoran respuestas tardías.
- **Universos:** creación de campañas adicionales junto al selector. Un fallo de creación mantiene la campaña actual y el nombre escrito para reintentar.
- **Atlas:** los extremos de una relación abren su ficha por identificador, con retorno a relaciones o al listado. La selección recibe foco y no depende de coincidencias de nombres.
- **Presentación:** cabecera más compacta, fuentes antes del mantenimiento, acciones de gestión y operaciones finalizadas plegables, sin espacio vacío reservado a una respuesta inexistente. Las consultas explican que el Atlas se mantiene por separado.
- **Lector:** diálogo modal nativo, fondo inerte, punto de entrada explícito, ciclo de Tab/Shift+Tab, Escape y retorno al control que inició la lectura, incluso con carga asíncrona. Cierre con área mínima de 44 px y anchura adaptada a móvil.
- **Entrega:** prueba de navegador añadida a CI; guía de demo de ocho minutos; alcance actualizado; protocolo para probar utilidad con una campaña real. Las tres páginas modificadas de `docs` se incluyen explícitamente en Git. Los informes y trazas de Playwright se excluyen del contexto de construcción de Docker.

## Comprobaciones de esta entrega

- Backend: `mvnw.cmd --batch-mode --no-transfer-progress verify`: **185 tests**, cero fallos, errores u omitidos; BUILD SUCCESS.
- Frontend: `npm run verify`: **113 tests**, lint, cobertura y compilación correctos. Cobertura de líneas **87,32 %**, ramas **78,46 %**; umbrales sin reducir. La cobertura de Vitest no incluye la ejecución de teclado en Chromium.
- Lector en Chromium: prueba específica con el documento original de Lumbrevela, foco y móvil de 390 × 844: **correcta**.
- Aceptación completa desde volúmenes vacíos: **2 escenarios correctos en 3,8 minutos**, sin reintentos del runner. Incluye propietario y dos jugadores, invitaciones consecutivas, fallo transitorio, reemplazo fallido con la versión anterior publicada, reintento manual, búsqueda y lectura, citas literales, teclado, móvil y creación de un segundo universo con vuelta al primero. Keycloak y la aplicación alcanzaron estado saludable antes de iniciar el navegador.

Los fallos intermedios de teclado se reprodujeron con documentos cortos y largos y se corrigieron antes de la ejecución final. `autoFocus` de React por sí solo no resolvió el foco inicial al abrir el diálogo; se fija tras `showModal()` y se conserva el control de origen antes de comenzar la petición.

Capturas de la versión final con un documento del corpus original: [biblioteca en escritorio](assets/2026-09-15/library-desktop.png) y [lector en móvil](assets/2026-09-15/reader-mobile.png). Son cuentas y datos de prueba locales.

Los contenedores, la red y los dos volúmenes de `codex-of-realms-e2e` se retiraron al terminar. No se desplegaron estos cambios sobre los datos del entorno principal.

## Límites y siguiente decisión

No se ha modificado ni vuelto a certificar la selección real de IA, ni se ha repetido la recuperación de backups en esta entrega de frontend. Los resultados históricos permanecen separados. La configuración experimental sigue fuera de esta intervención.

El historial administrativo del backend continúa sin paginación y con consultas por trabajo; plegarlo visualmente no elimina ese coste. Sigue pendiente medirlo y acotar el listado antes de trabajar con historiales grandes.

La [guía de demo](../docs/operations/DEMO.md) utiliza el corpus original existente y describe su preparación por la interfaz. No se ha creado un seed oculto ni cargado datos en el entorno principal. La prueba del lector sí carga un documento original en su entorno desechable.

El siguiente paso de producto es realizar el [protocolo con una campaña real](../docs/product/USER_VALIDATION.md), registrar las tareas que requieren ayuda y decidir el contrato entre Atlas y consultas. No se han realizado sesiones con usuarios ni se atribuyen resultados a ellas. La ejecución remota de GitHub Actions se comprobará cuando estos cambios se publiquen.
