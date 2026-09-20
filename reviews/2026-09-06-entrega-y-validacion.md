# Invitaciones verificadas, extractos y trabajos de fuentes

Entrega del 6 de septiembre de 2026: aceptación de invitaciones con correo verificado, respuestas extractivas y trabajos persistentes de fuentes. Frontend y backend deben desplegarse juntos. Consulta la [guía de operación](../OPERATIONS.md).

## Comportamiento entregado

- Las invitaciones permanecen pendientes hasta una petición del destinatario con correo coincidente y `email_verified: true` booleano en su JWT. La aceptación y la membresía comparten transacción y bloqueo. La verificación almacenada no activa invitaciones nuevas.
- Keycloak verifica el correo mediante Mailpit. El script administrativo actualiza los realms existentes de forma idempotente. En Keycloak 26.7 el usuario establece su contraseña después de verificar el correo.
- El modelo selecciona identificadores de hasta tres párrafos. El servidor copia sus textos y offsets originales, valida la selección y vuelve a comprobar autorización y versión activa. La copia es literal; aun así, el pasaje puede ser incorrecto o no responder a la pregunta.
- PostgreSQL conserva trabajos, intentos, progreso, errores seguros e historial. Los archivos se guardan atómicamente antes de responder 202. Las ejecuciones usan concesiones renovables, exclusión entre workers y publicación transaccional. Un reemplazo fallido conserva la versión publicada.
- El cliente muestra procesamiento, reintentos, reemplazo y reprocesamiento, con consultas periódicas cancelables. El backup coordina base y archivos deteniendo temporalmente el backend y restaurando su estado previo.

## Verificación de datos y operación

Resultado final de las comprobaciones:

| Comprobación | Resultado |
|---|---|
| `mvn verify` con Docker | 167 pruebas, cero fallos, errores u omisiones; build correcto |
| Frontend | 64 pruebas; lint y build correctos |
| Navegador aislado | Recorrido completo aprobado con propietario y dos jugadores, 3,7 minutos |
| Actualización de Keycloak existente | Dos ejecuciones consecutivas correctas |
| Restauración de V6 | Correcta, incluido arranque de Keycloak |
| Restauración de V8 con trabajo pendiente | Correcta; el worker terminó y publicó la versión |
| Compose | Configuraciones principal, navegador y restauración válidas |

El conjunto determinista mantiene sus exigencias: 13 preguntas respondibles resueltas, recuperación de fuentes de referencia, citas literales autorizadas y todos los rechazos de seguridad esperados. El doble de embeddings conserva conceptos cortos como «año» y elimina el sesgo de palabras gramaticales; este doble no certifica calidad con modelos reales. Sus métricas están en [deterministic-results.json](2026-09-06-deterministic-results.json).

La instalación local pasó de V6 a V8. Antes de migrar se creó `backups/20260906-021553`: había cero usuarios, membresías y documentos de aplicación, y cuatro usuarios de Keycloak. Estos recuentos se conservaron. Una prueba de migración adicional parte de V6 con usuarios, membresías, una versión publicada y otra interrumpida, y comprueba conservación y conversión de la operación pendiente a fallo visible.

El script de Keycloak se ejecutó dos veces correctamente sobre el realm existente. Las credenciales administrativas permanecen dentro del contenedor. El registro de aceptación usa una instancia aislada de Keycloak con PostgreSQL y correo real capturado en Mailpit.

El paso de contraseña posterior al correo corresponde al flujo de registro documentado en la [guía de administración de Keycloak](https://www.keycloak.org/docs/latest/server_admin/#_user-registration).

Se respaldó el entorno de navegador mientras había un reemplazo `QUEUED`, con dos archivos originales. La copia `backend/target/acceptance-backups/20260906-103534` se restauró en volúmenes nuevos. Se verificaron hashes y estado, arrancó Keycloak contra su esquema restaurado y el worker completó el trabajo y publicó la versión. El entorno de restauración se eliminó después de la comprobación.

Los cuatro escenarios del contrato de backup pasan: backend inicialmente en marcha, inicialmente detenido, error de copia y esquema anterior a V8. Los scripts PowerShell se analizaron sintácticamente y `git diff --check` no detectó problemas.

Al terminar se retiraron los contenedores, la red y los volúmenes de `codex-of-realms-e2e`. Solo permanece el proyecto Compose principal de esta aplicación. Los entornos de restauración también se eliminaron. Los backups y resultados se conservan como artefactos locales.

## Evaluación independiente con Ollama real

Se evaluaron los ocho modelos ya instalados, con una repetición de 22 casos: 21 se ejecutan en el selector y uno de autorización se delega a las pruebas autenticadas. Se usan fuentes visibles elegidas por el conjunto de referencia y párrafos ordenados por coincidencia léxica. Este experimento **no mide la recuperación vectorial real**. Los informes anteriores de generación libre no se utilizan para certificar la nueva respuesta extractiva.

| Modelo | Resultado esperado | Cobertura de hechos de referencia | Citas sobre casos respondibles | Mediana caliente | P95 caliente |
|---|---:|---:|---:|---:|---:|
| qwen3.5:4b | 95,2 % | 85,2 % | 92,3 % | 2.210 ms | 3.136 ms |
| qwen3:4b | 95,2 % | 85,2 % | 92,3 % | 1.828 ms | 2.668 ms |
| gemma4:e2b-it-qat | 95,2 % | 81,5 % | 92,3 % | 1.093 ms | 1.644 ms |
| granite4.2:3b-q4_K_M | 95,2 % | 81,5 % | 92,3 % | 1.285 ms | 1.821 ms |
| ministral-3:3b-instruct-2512-q4_K_M | 95,2 % | 88,9 % | 92,3 % | 1.584 ms | 2.151 ms |
| nemotron-3-nano:4b | 66,7 % | 37,0 % | 46,2 % | 1.568 ms | 2.640 ms |
| phi4-mini:3.8b-q4_K_M | 47,6 % | 14,8 % | 15,4 % | 698 ms | 2.691 ms |
| LiquidAI/lfm2.5-1.2b-instruct:q4_k_m | 38,1 % | 0 % | 0 % | 940 ms | 1.078 ms |

La cobertura de hechos es una medida léxica de relevancia para evaluación; no certifica veracidad. Las respuestas emitidas conservaron literalmente los originales visibles y no se detectaron filtraciones en este corpus. Los modelos que rechazan todo no demuestran utilidad por tener cero fallos de literalidad.

De los 21 casos evaluados por modelo, Qwen 3.5, Qwen 3, Gemma, Granite y Ministral rechazaron 9; Nemotron rechazó 15, Phi rechazó 19 y LFM rechazó 21. Ocho casos esperaban rechazo; el resto son preguntas respondibles rechazadas.

**Ningún modelo superó todos los umbrales de calidad.** El caso `gm-003` se rechaza por relevancia antes de llamar al modelo en este experimento. Algunos modelos rechazan muchas más preguntas. Se conservan los resultados, rechazos, latencias de arranque, configuración y digests en [selector-results.json](2026-09-06-selector-results.json). La comparación no constituye una certificación general de calidad con IA real.

## Evidencias locales

- `backend/target/acceptance-verify.log`: suite completa de Maven y pruebas PostgreSQL.
- `frontend/acceptance-{lint,tests,build}.log`: comprobaciones del cliente.
- `frontend/acceptance-e2e.log` y `frontend/playwright-report/`: recorrido del propietario y dos jugadores.
- `backend/target/acceptance-keycloak-update.log`: actualización idempotente del realm existente.
- `backend/target/acceptance-pending-{backup,restore}.log`: copia y recuperación con trabajo pendiente.
- `backend/target/acceptance-legacy-restore.log`: restauración del esquema anterior.

Los logs, backups y trazas contienen solo artefactos locales y quedan excluidos de Git. Los datos resumidos de la evaluación se conservan en este directorio para revisar los resultados sin depender de los artefactos ignorados.
