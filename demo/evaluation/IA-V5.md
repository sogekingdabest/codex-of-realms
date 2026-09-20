# Contexto y omisiones: evaluación v5

## Variante congelada

Se mantienen Qwen3.5:4b, bge-m3, los límites 10/6/3 de recuperación/pasajes/citas, 2.000 caracteres por pasaje, 6.000 por respuesta y 8.192 tokens de contexto. La variante utiliza recuperación híbrida con cobertura léxica 1,00, cobertura de pregunta 0,70 y prompt v3. No se ajustan umbrales.

LORE_CONTEXTUAL_PASSAGES_ENABLED une párrafos consecutivos de la misma sección cuando el intervalo completo no supera 2.000 caracteres. Solo une a través de espacios originales; nunca atraviesa encabezados, texto excluido o ventanas solapadas. Copia el intervalo original completo y mantiene sus offsets UTF-16. Las lecturas agrupadas por versión y la revalidación final de permisos/versión/literalidad siguen vigentes.

QA_COMPLETE_SELECTION_ENABLED rechaza omisiones detectables después de validar los IDs. Divide preguntas en partes cuando una conjunción introduce otro interrogativo, y comprueba que cada parte conserve la cobertura léxica disponible hasta 0,70. Para cantidades exige un numeral y los nombres cortos explícitos en el mismo pasaje, además de la cobertura de esa parte. Reconoce cifras y formas comunes de numerales españoles. El prompt v3 recibe questionParts y pide verificar cada parte, la correspondencia de cantidades y sus sujetos y los antecedentes de referencias causales.

El control puede pasar por alto omisiones o rechazar respuestas válidas: no reconoce todas las coordinaciones, referencias, paráfrasis ni formas de expresar cantidades. Si detecta una omisión, devuelve VALIDATION_FAILED. Conserva TextTerms y no añade citas, reescribe texto ni realiza otra llamada al modelo.

Ambas opciones permanecen desactivadas por defecto hasta superar aceptación. El prompt por defecto sigue siendo v1. No hay migración, regeneración de embeddings ni cambios de API.

## Evaluación

ia-v5.json contiene 24 preguntas nuevas de validación, con originales de biblioteca y torre separados de los conjuntos usados para desarrollar v3/v4. Incluye causas entre párrafos, cantidades escritas, sujetos distintos, excepciones, dos fuentes, distractores y permisos. ia-v5.freeze.json registra hashes y configuración antes de medir. Los casos conocidos v3 siguen como regresiones, no como validación independiente. No hay búsqueda de nuevos umbrales en esta iteración: el comportamiento se fijó mediante contratos y regresiones antes de validar.

Ejecutar en el Ollama dedicado descrito en [IA.md](IA.md):

~~~powershell
.\scripts\evaluate-ia-v5.ps1 -OllamaBaseUrl http://127.0.0.1:11435 -DedicatedEndpoint
~~~

El runner ejecuta tres repeticiones de referencia actual (vector + prompt v1), tres de candidata v5 y tres de regresión v3, todas sobre el mismo corpus ampliado (IA_CORPUS_VERSION=5). Los controles históricos conservan sus corpus por defecto. Registra digests, archivos, hardware, contexto y comprobación de omisiones habilitados, partes omitidas detectadas, citas, hechos y latencias.

Se mantienen los criterios: cada repetición debe superar exactitud ≥80 %, hechos ≥70 %, estructura y citas ≥95 %, sin fugas ni citas alteradas, y sin regresión de calidad respecto a la referencia. La peor P95 completa debe estar dentro de +20 %. Además, deben superarse gm-002, gm-003 y todos los casos obligatorios de contexto en las tres repeticiones. El runner nunca activa la variante automáticamente: exige revisión de contexto y deja registrado el bloqueo si falla.

Los servicios y datos son aislados; no se descargan pesos ni se descarga de memoria una instancia habitual. Un fallo de infraestructura se registra como pendiente y no se confunde con un fallo de calidad. Para evaluar calidad general y rendimiento harán falta corpus más amplios y variados.

## Resultado

La primera ejecución terminó en ACCEPTANCE_BLOCKED: mejora el contexto y el caso de las monedas, pero el control léxico genera falsos rechazos. Las opciones siguen desactivadas. Véase el [informe de entrega](../../reviews/2026-09-13-contexto-y-omisiones.md).
