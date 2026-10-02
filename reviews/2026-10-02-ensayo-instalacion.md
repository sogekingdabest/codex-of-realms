# Ensayo de instalación de la versión candidata

Revisión del commit `8765c9b` de `main`, descargado desde GitHub en una copia temporal. El ensayo usa contraseñas, base de datos, almacén de documentos y nombre de proyecto nuevos (`codex-beta-rehearsal-20261002`), separados de la instalación habitual. Sigue el método del [ensayo del 20 de septiembre](2026-09-20-ensayo-instalacion.md) y añade la campaña de ejemplo y la restauración de copias.

## Resultado

La versión candidata se instala desde cero, carga la campaña de ejemplo con los modelos reales, responde sin filtrar contenido restringido y su copia de seguridad se restaura. No se encontró ningún fallo que impida la beta. Hay un detalle de interfaz, descrito más abajo.

| Comprobación | Resultado | Tiempo |
|---|---|---:|
| [CI del commit](https://github.com/sogekingdabest/codex-of-realms/actions/runs/37028219209) | Backend con 200 pruebas; frontend con 216 pruebas, lint, cobertura y compilación; Compose; navegador con dos escenarios y la carga de la campaña en Linux | — |
| Compilación de las imágenes | Correcta, con la caché de Docker de compilaciones anteriores | 18 s |
| Arranque con GPU hasta servicios sanos | Correcto; tema propio en Keycloak, realm sin usuarios y ningún universo | 111 s |
| `./scripts/load-demo-campaign.ps1` con sus valores por defecto | 3 cuentas, 7 fuentes, 12 fichas y 9 relaciones; cada cuenta ve exactamente lo suyo | 75 s |
| Consultas reales en el navegador | Respuestas citadas para Tala y ninguna fuga hacia Oren | Ver tabla |
| `./scripts/backup.ps1` | Base de datos, 7 originales y manifiesto con el commit; ningún trabajo pendiente; backend en marcha al terminar | 9 s |
| `./scripts/verify-restore.ps1` | 8 migraciones, 7 originales con sus huellas y Keycloak arrancado sobre el esquema restaurado | 58 s |

## Campaña de ejemplo

El script se ejecutó nada más arrancar, mientras el backend cargaba los modelos. La primera fuente se publicó 29 s después de la subida, porque el primer embedding esperó a que `bge-m3` estuviera en la GPU. Las siete estaban publicadas a los 62 s. La comprobación final del script coincidió con lo esperado: Inés ve 7 fuentes, 12 fichas y 9 relaciones; Tala, 5, 10 y 7; Oren, 3, 9 y 6.

## Consultas

Tala tiene revelado el spoiler «Recuerdos de Nara»; Oren solo ve el contenido público. Los tiempos salen del registro de Ollama.

| Cuenta | Pregunta | Resultado | Tiempo |
|---|---|---|---:|
| Tala | ¿Dónde se alza Lumbrevela? | Respuesta citando «El Meridiano y la ciudad de Lumbrevela» | ≈57 s |
| Tala | ¿Quién es Nara Ors? | Respuesta citando dos pasajes de «El recuerdo de Nara» | ≈8 s |
| Oren | ¿Quién es Nara Ors? | «No hay una respuesta verificable» | ≈1 s |
| Oren | ¿Quién fundó la universidad de Aramonte? | «No hay una respuesta verificable» | ≈5 s |
| Oren | ¿Por qué las marcas antiguas no concuerdan con los mapas oficiales? | «No hay una respuesta verificable» | ≈12 s |

La primera respuesta tras arrancar volvió a ser lenta, como el 1 y el 2 de octubre: 56 s de generación con el modelo ya cargado. Las siguientes tardaron segundos. Ollama volvió a avisar de presión de memoria y desactivó `mmap` en la máquina virtual de Docker, de 6,7 GiB.

Las tres preguntas de Oren terminaron sin llamar al modelo de chat: la búsqueda no le devolvió ningún pasaje suficiente. La última solo tiene respuesta en una nota de dirección. Son cinco observaciones, no una evaluación de calidad.

## Detalle encontrado

El ejemplo del cuadro de consulta es fijo: «¿Por qué la Aguja conserva una deuda antigua?». Lo ven todas las cuentas, también los jugadores. En la campaña de ejemplo insinúa el secreto de dirección «La deuda de la Aguja», y en cualquier otra campaña habla de un mundo ajeno. Se corrigió después del ensayo: el ejemplo pasa a ser «Pregunta por un lugar, un personaje o un suceso de la campaña». Los demás ejemplos que nombran el mundo de la demo solo aparecen en pantallas de dirección o al crear un universo, y no revelan nada.

## Límites

- La compilación reutilizó la caché de Docker. La compilación sin caché la cubre la CI.
- Los pesos de los modelos se copiaron del volumen del ensayo anterior, montado solo en lectura. Como el 20 de septiembre, el ensayo no acredita una descarga completa.
- Las cuentas del script ya están verificadas. El registro con Keycloak y Mailpit lo cubren la CI y el ensayo anterior.
- No había trabajos pendientes al hacer la copia, así que la reanudación con `-ResumePendingJobs` no se ejercitó.
- Un solo equipo, acceso por `localhost`, sin correo real ni lector de pantalla.

La instalación habitual se detuvo durante el ensayo porque usa los mismos puertos, y se volvió a arrancar al terminar; sus datos no se tocaron. Los contenedores del ensayo se eliminaron y sus volúmenes se conservan.
