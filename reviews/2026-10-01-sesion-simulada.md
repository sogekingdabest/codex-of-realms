# Sesión simulada con dirección y dos jugadores

Revisión del commit `64aed42` de la rama `feat/frontend-reading-and-routing`. No hay jugadores disponibles para la [sesión con usuarios](../docs/product/USER_SESSION_ES.md), así que se ha simulado con tres personas guionizadas. Este informe no sustituye a la observación de personas reales: muestra si cada tarea tiene un camino visible y qué falla por el camino.

## Cómo se ha simulado

- **Entorno.** `compose.e2e.yaml` aislado, con base de datos, Keycloak y Mailpit nuevos. El servidor de pruebas de Ollama se sustituyó por Ollama 0.33.2 con `qwen3.5:4b` y `bge-m3` reales en una RTX 3060 Laptop de 6 GiB. Los pesos se montaron en solo lectura desde el volumen del ensayo del 20 de septiembre. La instalación habitual no se tocó.
- **Personas.** Inés dirige la partida desde un portátil. Tala juega desde un portátil y recibe el spoiler «Recuerdos de Nara». Ivo juega desde un móvil de 390 × 844 px y recibe «La campana de vidrio». Las tres se registran por la página de Keycloak en español y verifican su correo en Mailpit.
- **Guion.** Un script de Playwright sigue las tareas del guion en orden. Cada persona actúa solo con nombres y etiquetas visibles. Antes, la dirección prepara la campaña desde la interfaz siguiendo la [guía de demo](../docs/operations/DEMO.md).
- **Medidas.** Se cuentan las interacciones del camino mínimo (clics, campos, teclas y navegaciones) y el tiempo de máquina. Hay una captura en cada punto de decisión, revisada después desde el papel de cada persona.
- **Ejecuciones.** Una ejecución preliminar, en la que se produjo el fallo en frío; una con los modelos descargados; y una final con los modelos cargados. Las iteraciones anteriores solo corrigieron el guion.

## Resultado

Las siete tareas terminan por un camino visible y **no hay ninguna filtración**. La tabla resume la ejecución final.

| Tarea | Persona | ¿Termina? | Interacciones | Qué ocurrió |
|---|---|---|---:|---|
| 1. Dónde se alza Lumbrevela | Tala, escritorio | Sí | 6 | Pregunta en Consultas. El pasaje citado llega en 6,1 s y se abre marcado en el documento original. |
| 1. Dónde se alza Lumbrevela | Ivo, móvil | Sí | 4 | Empieza por el Atlas. La ficha de Lumbrevela ya muestra el fragmento original sin abrir nada más. |
| 2. Buscar un documento por nombre | Ivo, móvil | Sí | 4 | «rutas» deja 1 de 3 fuentes. Lee el documento sin usar el modelo. |
| 3. Nota privada y spoiler | Inés | Sí | 14 | Crea «La campana de vidrio», marca a Ivo y sube la nota privada y el spoiler. |
| 3. Qué puedo ver | Tala | Sí | 6 | Ve «El recuerdo de Nara» como «Revelado para ti». No ve las notas privadas ni el spoiler de Ivo. |
| 3. Qué puedo ver | Ivo, móvil | Sí | 11 | Ve «La campana de vidrio». Nada de Nara en la biblioteca, el Atlas, las relaciones ni las consultas. |
| 4. Sustituir una nota | Inés | Sí | 3 | Mientras procesa: «La versión 1 sigue publicada y disponible para consultas». |
| 5. Seguir una relación | Tala | Sí | 3 | «La Aguja del Mediodía *se encuentra en* Lumbrevela» lleva a la ficha de Lumbrevela con su propia URL. |
| 6. Otra campaña y volver | Inés | Sí | 5 | Para volver hay que desplegar «Propietario · Cambiar universo». |
| 7. Dato ausente | Ivo, móvil | Sí | 3 | «No hay una respuesta verificable» en 0,3 s, aunque queda fuera de la pantalla (problema 2). |

La preparación requirió 73 interacciones de la dirección, sin contar el registro: universo, cinco fuentes, dos invitaciones, un grupo de spoiler, tres fichas, una relación y la consulta de control.

### Permisos

- Fuentes, fichas y relaciones de los dos jugadores, revisadas tras cada cambio.
- La URL directa de una nota privada, abierta por Tala: muestra «Esta fuente no está disponible» sin el título.
- Dos consultas de Ivo dirigidas al contenido oculto. «¿Quién es Nara Ors y dónde está?» se abstiene. «¿Avanza realmente el Meridiano hacia el oeste?» solo cita fuentes públicas.

La dirección ve quién tiene cada spoiler («Spoiler · Tala Mir», «Spoiler · Ivo Sanz»).

## Problemas encontrados

### 1. La primera consulta tras arrancar puede acabar en un error genérico

En la ejecución preliminar, la consulta de control de la dirección mostró este aviso tras 3 minutos: «El servidor no pudo completar la operación. Inténtalo de nuevo en unos minutos». El modelo de chat todavía se estaba cargando. Los registros explican la cadena:

1. El backend esperó a Ollama 120 s y registró `ReadTimeoutException`.
2. Tres segundos después, Ollama recibió un segundo `POST /api/chat` con la misma petición. Era el reintento de Spring AI: en la versión 2.0.1, `spring.ai.retry.max-attempts: 1` se traduce en `maxRetries(1)`, es decir, una llamada más tras la primera, con 2 s de espera.
3. nginx cortó a los 180 s y devolvió un 504 sin el detalle del problema. La interfaz no pudo mostrar «No se pudo consultar el modelo».
4. Ollama terminó ambas generaciones para nadie: tardaron 3 min 35 s y 1 min 29 s. La primera consulta de Tala esperó 41 s en vez de unos 6 s.

El arranque en frío varía mucho. La primera vez tras arrancar el contenedor, solo cargar el modelo llevó 2 min 6 s. En el ensayo del 20 de septiembre, la primera consulta completa tardó 65 s; con los pesos en la caché de disco, 27 s. La primera indexación también esperó 1 min 17 s a que cargara `bge-m3`. Durante toda la espera, el botón dice «Buscando evidencia…» sin progreso ni opción de cancelar.

**Propuesta.** Alinear los tiempos de espera: el backend debe responder antes de que corte el proxy y no reintentar un `POST` al modelo. Además, ofrecer a la dirección un estado «Preparando modelos» con calentamiento explícito, y mostrar etapas y cancelación en la consulta.

### 2. En móvil, el resultado de una consulta aparece fuera de la pantalla

Después de pulsar «Consultar», la pantalla no se mueve y el foco tampoco. El resultado empieza en el píxel 859 de una ventana de 844, así que Ivo no ve que ha llegado ([captura](assets/2026-10-01-sesion-simulada/resultado-fuera-de-pantalla-movil.png)). La región `aria-live` lo anuncia a un lector de pantalla, pero no a quien mira el móvil. En escritorio el pasaje sí queda visible.

**Propuesta.** Al recibir el resultado, mover el foco a su encabezado y desplazarlo a la vista.

### 3. El diálogo de evidencia enseña datos técnicos a los jugadores

Abierta desde una ficha, la cabecera muestra «Procedencia del canon · 3fb51c5251» y «01-el-meridiano-y-lumbrevela.md» ([captura](assets/2026-10-01-sesion-simulada/evidencia-con-archivo-movil.png)). El lector de fuentes ya oculta el nombre del archivo a los jugadores; el diálogo no sigue la misma regla.

### 4. El registro no parece parte del producto

Es el primer contacto de un jugador invitado. Usa el tema por defecto de Keycloak, trata de usted y, tras verificar el correo, pide «Modificar contraseña: Tiene que cambiar su contraseña para activar su cuenta» a alguien que nunca tuvo una ([captura](assets/2026-10-01-sesion-simulada/registro-modificar-contrasena.png)).

**Propuesta.** Un tema de acceso con la identidad del archivo y textos propios, por ejemplo «Crea tu contraseña».

### 5. La biblioteca no indica que hay fuentes procesándose

Mientras se procesan las tres primeras fuentes, la columna principal dice «El archivo empieza aquí · Añade un documento…» y el índice «Todavía no hay fuentes visibles» ([captura](assets/2026-10-01-sesion-simulada/fuentes-procesando-sin-aviso.png)). El progreso solo aparece al final del índice, en «Procesamiento de fuentes». Con el modelo frío, esa espera superó el minuto.

### 6. En móvil, la cabecera ocupa media pantalla

El título de la campaña, el rol, «Nuevo universo», las secciones y «Abrir índice» ocupan unos 450 de los 844 px. Al abrir un documento, el texto empieza por debajo del pliegue ([captura](assets/2026-10-01-sesion-simulada/lectura-movil.png)); en el Atlas, la primera ficha asoma al final.

**Propuesta.** Una cabecera compacta en móvil, sacar «Nuevo universo» del primer plano de los jugadores y desplazar la vista al contenido abierto.

### 7. Detalles para la dirección

- El formulario de subida vuelve a «Solo dirección» tras cada archivo. Es un valor seguro, pero subir tres fuentes públicas obliga a elegir «Público» tres veces.
- Las opciones de visibilidad se nombran distinto según el formulario. La subida dice «Público · Pública» y «Recuerdos de Nara · Spoiler con permiso»; el Atlas dice «Público» y «Recuerdos de Nara», sin indicar que es un spoiler.
- Al elegir el archivo de sustitución, la confirmación aparece al final del índice, lejos de la acción. «Reprocesar» tiene estilo de acción principal junto a «Reemplazar archivo». El progreso muestra «0/0 fragmentos · 0 intentos» y «1 intentos».
- Con una biblioteca larga, la página entera se desplaza y la columna principal queda vacía.

### 8. Misma pregunta, distinto resultado

«¿Avanza realmente el Meridiano hacia el oeste?» se respondió con cita en tres ejecuciones. En otra, el modelo respondió pero no superó la validación de evidencia y citas. La validación evita una respuesta sin respaldo, pero la variación debería entrar en la evaluación de modelos.

## Comparación con las notas habituales

No se puede simular de forma justa. Como referencia, «Lumbrevela» aparece 12 veces en los tres documentos públicos; en el archivo correcto, la respuesta está en la cuarta aparición. Un jugador no tendría esos archivos, y una carpeta compartida no separaría las notas privadas.

## Límites

- Un guion escrito por alguien que conoce la interfaz sigue el camino mínimo: no duda, no se equivoca y no lee. «Termina» significa que existe un camino visible, no que una persona lo encuentre. Los tiempos son de máquina.
- Las preguntas finales del guion no tienen respuesta: no se han inventado opiniones.
- Hay un solo equipo y una sola GPU. El fallo en frío depende de la caché de disco y no se reprodujo en las ejecuciones con los pesos ya leídos.
- Quedan fuera el acceso por red local o Internet, el correo real y la revisión con lector de pantalla.

## Prioridad propuesta

1. El fallo en frío: tiempos de espera alineados, calentamiento visible, progreso y cancelación. Corregido el mismo día; ver la sección siguiente.
2. Foco y desplazamiento al resultado de la consulta en móvil. Corregido; ver «Corrección del resultado fuera de pantalla».
3. Ocultar el archivo y el checksum a los jugadores en el diálogo de evidencia. Corregido; ver «Corrección del diálogo de evidencia».
4. Tema y textos propios en el registro.
5. Estado de procesamiento en la biblioteca y cabecera compacta en móvil.
6. Los detalles de la dirección.

## Corrección del fallo en frío

Se corrigió en la misma rama, en cuatro cambios:

- **Una llamada por petición.** `spring.ai.retry.max-attempts` pasa a `0`. Un test carga `application.yaml` en la configuración de reintentos de Spring AI y comprueba que una llamada que caduca se ejecuta una sola vez. Con el valor anterior, el test falla.
- **El API responde antes que el proxy.** nginx espera 300 s, más que las dos llamadas posibles de 120 s (embedding y chat). Un 504 que aún llegue al navegador explica que el modelo pudo estar cargándose.
- **Calentamiento.** `POST /api/v1/capabilities/warm-up` carga en segundo plano el modelo de embeddings y luego el de chat. El backend lo hace al arrancar y la web cuando alguien abre Consultas. La verificación con modelos reales encontró un fallo: el calentamiento cargaba el modelo de chat con el contexto por defecto (4096) y las respuestas piden 8192. Ollama lo recargaba en cada visita a Consultas, y las consultas pasaron a tardar entre 29 y 89 s. Ahora el calentamiento usa el mismo contexto que las respuestas.
- **Espera visible.** Junto al botón aparece un contador. A los 8 s, un mensaje explica que el modelo puede estar cargándose, y «Cancelar» devuelve el control sin error ([móvil](assets/2026-10-01-sesion-simulada/correccion-espera-movil.png), [escritorio](assets/2026-10-01-sesion-simulada/correccion-espera-escritorio.png)). Un fallo del modelo se muestra como «Modelo no disponible», con la sugerencia de reintentar en un minuto.

### Verificación

- Backend: 200 pruebas con Docker, incluidas las de integración, sin fallos ni omisiones. Frontend: lint, 194 pruebas, cobertura y compilación. La suite de navegador con el modelo determinista pasa.
- Con los modelos reales, recién arrancado el contenedor, el calentamiento de arranque cargó `bge-m3` en 35 s y `qwen3.5:4b` en 1 min 28 s sin que nadie preguntara. La sesión completa pasó sin ninguna recarga del modelo, y las consultas de los jugadores tardaron entre 3 y 8 s.
- La consulta de control tardó aún 65 s con el modelo ya cargado. Medido directamente contra Ollama con la caché caliente, la primera consulta tras cargar tarda 1,8 s. La máquina virtual de Docker tiene 6,7 GiB, usaba 1,1 GiB de swap y Ollama avisó de que desactivaba `mmap` por presión de memoria. Queda como observación de este equipo, no como fallo reproducido.
- Para probar el fallo, se recreó el backend con un timeout de 12 s y se descargó el modelo de chat. La consulta mostró «Modelo no disponible» a los 13,7 s, sin aviso de error ni 504 ([captura](assets/2026-10-01-sesion-simulada/correccion-modelo-no-disponible.png)). Ollama recibió una sola llamada de chat por la pregunta, más la del calentamiento, y ningún reintento. Siguió cargando el modelo, y la misma pregunta respondió en 2,5 s al repetirla 45 s después.

Ollama descarga el modelo tras cinco minutos sin uso (`AI_CHAT_KEEP_ALIVE`). Una pregunta después de una pausa todavía paga la carga, aunque ahora con explicación, sin llamada duplicada y con la opción de cancelar. Para una partida larga conviene ampliar ese valor.

## Corrección del resultado fuera de pantalla

Cuando llega un resultado nuevo, el foco pasa a su encabezado. Si ese encabezado no se ve, la página se desplaza hasta el resultado. En escritorio, donde ya se veía, la página no se mueve. La tarjeta con pasajes no tenía encabezado: «Fragmentos de las fuentes» pasa a serlo, con el mismo aspecto.

El foco no se mueve si la persona ya está escribiendo otra pregunta; la región `aria-live` sigue anunciando el resultado. Que el foco siga en la pregunta enviada no cuenta como escribir, porque iOS no lo pasa al botón al tocar «Consultar». Al volver a Consultas con un resultado anterior, el foco tampoco se mueve.

Verificación: cuatro pruebas nuevas del panel, de las que tres fallan sin el cambio (la cuarta cubre la vuelta a Consultas), y la suite de navegador, que ahora pregunta también a 390 × 844 y comprueba que el encabezado recibe el foco y queda entero en pantalla ([captura](assets/2026-10-01-sesion-simulada/correccion-resultado-movil.png)). No se repitió la sesión con modelos reales.

## Corrección del diálogo de evidencia

El diálogo sigue ahora la regla del lector de fuentes: solo la dirección ve el nombre del archivo y el checksum. Un jugador ve «Procedencia del canon» o «Evidencia autorizada · versión N», el título de la fuente y el apartado del pasaje («La Aguja» o, sin apartado, «Documento»). La dirección sigue viendo «Procedencia del canon · 3fb51c5251» y «01-el-meridiano-y-lumbrevela.md · Documento». El cambio es de presentación: la API sigue enviando esos datos al jugador, que ya tiene acceso a la fuente.

Verificación: una prueba nueva del diálogo con los dos roles y otra de la aplicación en la que un jugador abre la cita de una respuesta; ambas fallan sin el cambio. La prueba de la propietaria comprueba que la dirección sigue viendo el archivo. La suite de navegador comprueba además que el diálogo de un jugador no contiene el nombre del archivo.

## Reproducción y registros

El guion está en [`frontend/e2e/session/`](../frontend/e2e/session/user-session.spec.ts) y se ejecuta con `npm run test:session`. Corre contra `compose.e2e.yaml` más `compose.session.yaml` y `compose.gpu.yaml`, como explica [OPERATIONS.md](../OPERATIONS.md#reproducible-acceptance). Para esta revisión, los pesos salieron del volumen del ensayo del 20 de septiembre (`SESSION_MODELS_VOLUME=codex-beta-rehearsal-20260920_ollama-data`).

[Resultados por tarea de las dos ejecuciones completas y datos del fallo en frío](2026-10-01-sesion-simulada-results.json). [Capturas seleccionadas](assets/2026-10-01-sesion-simulada/).
