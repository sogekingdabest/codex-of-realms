# Evaluación del bloque de IA

`ia-v3.json` amplía el baseline con nueve casos de contexto, nombres cortos, cifras, condiciones y frases excluidas. Contiene 31 casos: seis para calibrar y 25 para validar. El umbral se elige solo con los seis primeros. Los originales adicionales están en `ia-sources/`; `baseline.json` y los informes anteriores se conservan.

## Entorno y ejecución

La evaluación requiere Java 21, Docker con soporte GPU, los tres modelos y `bge-m3` ya instalados. No descarga pesos. PostgreSQL se crea mediante Testcontainers; no se usa la base de datos de la aplicación. El inicio de sesión externo se sustituye por identidades JWT de prueba, conservando la autorización de la aplicación.

El entorno usado para esta evaluación guarda los modelos de chat en Ollama nativo y `bge-m3` en un volumen Docker. El Compose dedicado crea un catálogo privado con enlaces a esos pesos; monta ambos almacenes en **solo lectura**. No arranca los demás servicios de la aplicación.

```powershell
$env:IA_NATIVE_MODELS = Join-Path $env:USERPROFILE '.ollama/models'
# Si el volumen existente tiene otro nombre, establecer IA_OLLAMA_VOLUME.
docker compose -f compose.ia-evaluation.yaml up -d
Invoke-RestMethod http://127.0.0.1:11435/api/tags
./scripts/evaluate-ia.ps1 -OllamaBaseUrl http://127.0.0.1:11435 -DedicatedEndpoint
docker compose -f compose.ia-evaluation.yaml down
```

Esperar a que `/api/tags` responda antes de ejecutar. Se usa IPv4 porque el puerto está publicado en `127.0.0.1`. La opción `DedicatedEndpoint` confirma que el runner puede descargar **de memoria** los modelos de esa instancia entre mediciones. El evaluador antiguo solo lo hace al indicar esa opción explícitamente.

## Recorrido y decisiones

1. Referencia anterior al cambio: tres repeticiones sobre validación. Las cuatro clases `Reference*` de pruebas congelan segmentación, expansión, gate y orquestación anteriores; nunca son una opción de producción.
2. Calibración con `qwen3.5:4b`: umbrales 0,70 / 0,60 / 0,50 / 0,40, similitud 0,45. Minimizar rechazos respondibles sin aumentar respuestas indebidas ni reducir hechos; desempatar a favor del umbral mayor. Guardar la decisión antes de validar.
3. Validación: tres repeticiones de `qwen3.5:4b`, `ministral-3:3b-instruct-2512-q4_K_M` y `gemma4:e2b-it-qat` con la configuración congelada.
4. Cada repetición debe superar exactitud ≥80 %, hechos ≥70 %, JSON ≥95 %, citas ≥95 %, cero fugas y los casos obligatorios. `gm-003` exige citas de ambas fuentes; los casos de contexto exigen los extractos completos que contienen negaciones y condiciones.
5. Promoción: ninguna métrica de calidad puede empeorar frente a la referencia ni frente al modelo actual con la nueva selección; la peor P95 por repetición debe estar dentro del +20 % de ambas referencias. Priorizar hechos, exactitud, citas y, en empate, velocidad.

`decision.json` informa de la elegibilidad; no edita configuraciones. El responsable de la entrega revisa el contexto de las citas antes de aplicar modelo, digest y umbral en los valores por defecto. Un fallo técnico, un informe incompleto o ningún modelo apto dejan la promoción pendiente. No se reducen los criterios para obtener un ganador.

## Evidencias y límites

Cada ejecución guarda manifiesto de modelos/digests, hardware, código y dataset; configuración congelada; informes por candidato/repetición y decisión. Los informes incluyen recuperación autorizada, pasajes ofrecidos, borrador del selector, texto y offsets de citas, motivos de rechazo, consumo de tokens y tiempos de recuperación, expansión, modelo y petición completa. La recuperación incluye el embedding de la pregunta. Las comprobaciones adicionales del evaluador quedan fuera de la latencia de la petición.

El calentamiento debe completar una llamada real antes de medir repeticiones. Se registra aparte; la ingestión carga previamente el modelo de embeddings, por lo que no representa un arranque completo del sistema desde apagado. El timeout ampliado de evaluación permite cargar pesos; no modifica el timeout de producción.

La puntuación de hechos usa `TextTerms`, igual que el evaluador anterior. Mide coincidencias léxicas; las excepciones y negaciones requieren revisión del texto. La segmentación por frases no garantiza que toda dependencia semántica quepa en una ventana de 2.000 caracteres. Las frases individuales mayores se excluyen y se avisa en las operaciones y en el visor; no se reescriben ni se cortan.

Los contratos de calibración y promoción se prueban con `./scripts/test-ia-decision.ps1`. La evaluación aislada del selector sigue disponible mediante `./scripts/evaluate-local-models.ps1`; sus resultados no se confunden con la cadena real.

Al rellenar los huecos tras el reparto entre fuentes, se prioriza la cobertura adicional de la pregunta; los empates mantienen el orden de relevancia. Esto evita que varios pasajes redundantes desplacen una condición breve. Los rangos de las citas se vuelven a numerar de forma única tras la selección.

## Siguiente iteración

La recuperación complementaria y el prompt v2 se evalúan con [IA-V4.md](IA-V4.md). Los casos v3 son regresiones conocidas; la aceptación nueva usa el conjunto v4 congelado.
