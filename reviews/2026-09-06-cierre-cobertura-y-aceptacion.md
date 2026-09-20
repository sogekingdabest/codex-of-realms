# Cierre de cobertura y coherencia de aceptación — 6 de septiembre de 2026

El frontend supera los umbrales de cobertura pendientes en la auditoría. La verificación local y CI comparten `npm run verify`: lint, tests con cobertura, comprobación de LCOV y compilación de producción. Si una etapa falla o LCOV falta o está vacío, el comando se detiene.

Se mantienen los umbrales y las exclusiones de cobertura existentes. No se añaden dependencias, migraciones ni cambios al contrato HTTP del backend.

| Métrica global | Auditoría inicial | Resultado del cierre | Umbral |
|---|---:|---:|---:|
| Sentencias | 79,38 % | 85,76 % | 75 % |
| Ramas | 73,65 % | 78,09 % | 75 % |
| Funciones | 76,45 % | 81,95 % | 70 % |
| Líneas | 81,51 % | 87,50 % | 78 % |

**Pruebas y correcciones**

La suite pasa de 64 a **108 tests**, distribuidos en 14 archivos. Las 44 pruebas nuevas cubren seguimiento de trabajos, estados terminales, errores transitorios y de acceso, cancelación y respuestas tardías, recuperación de fuentes, fallos de subida/borrado, claves de idempotencia y controles de recuperación. Las suites dirigidas de este bloque contienen 47 tests, todos correctos.

Las pruebas revelaron defectos que se corrigieron con cambios acotados:

- Las claves automáticas ahora incluyen el tipo de operación: subida y reemplazo ya no comparten clave cuando coinciden el título y el ID del documento.
- Una lectura inicial lenta ya no sobrescribe el resultado más reciente del seguimiento ni repone fuentes después de recibir un error de acceso. La petición inicial se cancela al ser sustituida y sus resultados tardíos se ignoran.
- El parámetro de clave de reprocesamiento está declarado como `string`, igual que en la interfaz existente. TypeScript ya no lo restringe por inferencia al formato UUID cuando el llamador proporciona otra clave válida.

Los tests utilizan APIs simuladas, promesas controladas y temporizadores falsos; no requieren servicios externos. Las pruebas HTTP generan una respuesta nueva por petición.

**Coherencia de aceptación**

- [Comando común](../frontend/package.json) reutilizado por [CI](../.github/workflows/ci.yml) y el [script de aceptación](../scripts/verify-m8.2.ps1).
- Las instalaciones siguen utilizando `npm ci`; no se han cambiado dependencias ni regenerado el lockfile en este bloque.
- El informe PowerShell menciona explícitamente cobertura y LCOV y solo se genera después de completar todas sus etapas.
- README e instrucciones vigentes de operación, calidad, interfaz, Atlas y aceptación indican el comando común. `npm test` permanece como comprobación parcial de desarrollo.
- Los resultados históricos conservan su fecha y no se reinterpretan como evidencia de esta ejecución.

**Verificación ejecutada**

`npm run verify` terminó con código 0: lint, los 108 tests, los cuatro umbrales, LCOV y el build con TypeScript/Vite pasaron. El JavaScript de producción ocupa 271,91 kB, 81,90 kB gzip.

Se comprobó el comando real en directorios temporales con etapas simuladas: el caso válido llegó al build; un error de cobertura terminó con código 7; LCOV ausente y LCOV vacío terminaron con código 1. Ninguno de los tres casos fallidos ejecutó el build. También se verificaron la sintaxis del script PowerShell, la propagación de códigos de error de su función `Invoke-Checked` y `git diff --check`.

Los artefactos temporales de comprobación están bajo `backend/target/verify-contract-*`; no usan datos de la aplicación.

**Límite de este cierre**

Estas comprobaciones se ejecutaron en local. Quedan fuera de esta revisión el backend, el navegador, Docker, la restauración y los modelos reales. El workflow de CI se actualizó, pero no se ejecutó remotamente.
