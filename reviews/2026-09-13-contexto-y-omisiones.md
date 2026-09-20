# Contexto y omisiones: evaluación v5

## Decisión de entrega

La evaluación terminó en **ACCEPTANCE_BLOCKED**. El contexto agrupado, el control de omisiones y la recuperación híbrida siguen desactivados; se mantienen el prompt v1 y Qwen3.5:4b. Los criterios y el comportamiento quedaron fijados antes de validar.

La ejecución ia-v5-20260913-135804 terminó el 13 de septiembre a las 14:18. El [resumen reproducible](2026-09-13-ia-v5-results.json) conserva configuración congelada, digests, hardware, resultados de cada repetición y fallos. Los informes completos están en demo/evaluation/results/ia-v5-20260913-135804.

## Cambios y pruebas

- Agrupación de párrafos adyacentes de una sección cuando el intervalo literal completo cabe en 2.000 caracteres. Mantiene saltos originales y offsets UTF-16, sin atravesar encabezados ni frases excluidas. La lectura y revalidación del original siguen agrupadas por versión y autorizadas.
- Prompt v3 con las partes detectadas de la pregunta, instrucciones de conservar antecedentes y correspondencia entre cantidades y sujetos. El modelo continúa devolviendo solo identificadores; no hay segunda llamada ni redacción de texto.
- Control opcional de omisiones: cobertura por parte y comprobación de numerales/nombres cortos en el mismo pasaje. Se registra qué parte provoca el rechazo. Es una heurística; la evaluación demuestra que no es adecuada todavía como condición de aceptación general.
- Conjunto v5: 24 preguntas nuevas, con biblioteca y torre como dominios separados de v3/v4. Configuración y originales congelados antes de medir; sin búsqueda de nuevos umbrales. V3 se usa únicamente como regresión conocida.
- Backend: Maven verify, **185 pruebas**, cero fallos, errores u omisiones; BUILD SUCCESS a las 13:57:49. Incluye contexto exacto, límites 2.000/2.001, exclusiones, nombres Unicode, cantidades escritas, nombres parecidos y rechazo sin segunda llamada ni citas añadidas.
- Frontend: npm run verify, **108 pruebas**, lint, cobertura y build aprobados. git diff --check sin errores.

Los fallos de implementación detectados por las pruebas —límites de palabra con acentos y sourceId ausente en v3— se corrigieron antes de ejecutar la evaluación real.

## Resultados medidos

| Configuración | Exactitud de resultado | Hechos | Citas satisfactorias | P95 completa caliente por repetición (ms) | Primera petición fría (ms) |
|---|---:|---:|---:|---|---:|
| Referencia en v5 | 95,83 % | 90,48 % | 93,75 % | 7154 / 4140 / 3051 | 167645 |
| Candidata v5 | 95,83 % | 95,24 % | 93,75 % | 4746 / 3985 / 2856 | 29276 |
| Regresión v3 con candidata | 96 % | 80,77 % | 92,86 % | 5015 / 3896 / 3765 | 40896 |

Las métricas de calidad fueron iguales en las tres repeticiones. Todas dieron salida estructurada del 100 %, cero fugas detectadas, cero respuestas en casos etiquetados sin respuesta y ningún extracto devuelto inventado o alterado. La latencia cumple el límite de +20 %, pero no compensa el incumplimiento de citas/contexto. El arranque frío se mide aparte tras la ingestión y no representa un arranque completo del sistema.

## Qué mejora y qué falla

**Contexto causal:** v5-validation-01 pasa de rechazo a una respuesta que conserva la lluvia, la grieta, el deterioro de etiquetas y el cierre del archivo. Las dos causas esperadas se cubren. El modelo añade también el horario por tormenta, una cita redundante que conviene distinguir de la causa histórica.

**Pregunta de las monedas:** ia-val-multi incluye las 12 monedas de Li y la excepción del sello de Iria, con dos hechos y ambas fuentes en las tres repeticiones. La agrupación conserva la cantidad dentro del pasaje de su fuente; ya no se devuelve solo el distractor del catálogo.

**gm-002:** la cita ahora incluye el mecanismo de desplazamiento y la destrucción de registros junto a «Este es el motivo…». Mejora frente a la referencia aislada sin antecedentes, pero solo cubre uno de los dos hechos del evaluador congelado. Sigue faltando la evidencia específica sobre las posiciones históricas de observación. No se declara superado.

**Regresión provocada por el control léxico:**

- En v5-validation-13, el modelo selecciona correctamente la prohibición de sonar durante tormentas y la excepción por auxilio. El control rechaza esa selección por diferencias léxicas entre la pregunta («puede sonar») y el original («suena»). El resultado público es un falso rechazo.
- En gm-003, el modelo selecciona ambas fuentes y los pasajes correctos de riesgo y fecha, pero el control los rechaza. «Prevista» aparece en otros candidatos, mientras que la cita correcta expresa directamente la fecha sin repetir esa palabra; también hay diferencias entre «oculto» y «ocultado».

Esto confirma que la cobertura de palabras por parte no sirve como prueba de cobertura semántica. Los tests unitarios verifican los contratos mecánicos, mientras que la validación ha detectado el coste real de esa heurística. La función experimental se conserva desactivada para mantener la reproducción del resultado.

## Límites y siguiente decisión

El conjunto v5 ya es conocido y no debe reutilizarse para ajustar y volver a certificar el mismo cambio. Una siguiente iteración debería evaluar el contexto agrupado por separado y sustituir el rechazo léxico general por controles más específicos o diagnóstico, con nuevas preguntas independientes. No es suficiente bajar el umbral de este control.

Los parámetros experimentales siguen desactivados en application.yaml, Compose y el ejemplo de entorno. No hay migración, regeneración de embeddings ni cambio del contrato público. Procedimiento: [IA-V5.md](../demo/evaluation/IA-V5.md).
