# Recuperación complementaria y selección completa: entrega

## Decisión

La evaluación terminó en **ACCEPTANCE_BLOCKED**. La recuperación híbrida y el prompt v2 siguen desactivados por defecto. Se conservan Qwen3.5:4b, bge-m3, similitud 0,45, cobertura de pasajes 0,70 y prompt v1. Los límites de seis pasajes, tres extractos, 6.000 caracteres y 8.192 tokens se mantienen.

La ejecución ia-v4-20260908-213725 terminó el 8 de septiembre a las 22:14; la revisión y entrega se completan el día 9. Los resultados detallados locales están en demo/evaluation/results/ia-v4-20260908-213725. El [resumen conservado en el repositorio](2026-09-09-ia-v4-results.json) incluye métricas por repetición, fallos, configuración congelada, digests y auditoría.

## Implementación

- Recuperación léxica en español sobre el mismo conjunto autorizado que pgvector, con diez candidatos por vía, deduplicación y fusión RRF de pesos iguales y constante 60. Se retienen diez fragmentos antes de construir los seis pasajes. Similitud coseno y puntuación de fusión permanecen separadas.
- La vía léxica exige dos términos distintos coincidentes en un mismo fragmento y cobertura configurable. No relaja ni sustituye el control posterior de cobertura de la pregunta. Las señales léxicas no se exponen en el JSON público.
- Prompt v2 optativo para cubrir preguntas completas, conservar condiciones y excepciones y combinar fuentes complementarias, con un sourceId estable. La salida sigue siendo exclusivamente outcome y passageIds; no hay texto generado, segunda llamada ni citas añadidas automáticamente.
- Diagnósticos internos de gate, similitud máxima, términos coincidentes/ausentes, cobertura, candidatos, selección y latencias de embedding, SQL, expansión, modelo y petición completa. El API conserva sus motivos públicos de rechazo.
- Dataset v4 congelado antes de medir: 16 casos de calibración y 24 de validación, con fuentes separadas por familia. V3 se utiliza como regresión conocida. No se modificó TextTerms ni se ajustó el sistema después de validar.

No hay migración ni regeneración de embeddings. La auditoría comprobó los 42 fragmentos almacenados frente al embedding individual del mismo texto: coseno 1,0 en todos, sin desalineación observada entre textos y vectores. La revisión del código confirma que el trabajador concatena los lotes en orden y que la persistencia asigna cada vector al fragmento del mismo índice.

## Evaluación

Las seis variantes de calibración (prompt v1/v2 por coberturas léxicas 1,00/0,90/0,80) lograron exactitud, hechos y citas del 100 %, sin respuestas indebidas. La referencia obtuvo 93,75 %, 92,31 % y 90 %, respectivamente. Por desempate se congeló **hybrid-v1, prompt v2 y cobertura léxica 1,00** a las 20:00:24 UTC, antes de validar.

| Medición | Exactitud | Hechos | Citas satisfactorias | P95 completa caliente por repetición (ms) | Primera petición fría (ms) |
|---|---:|---:|---:|---|---:|
| Referencia v4: vector + prompt v1 | 100 % | 95 % | 100 % | 3231 / 2820 / 2607 | 14325 |
| Candidata v4: híbrida + prompt v2 | 100 % | 95 % | 100 % | 3308 / 3346 / 3171 | 20814 |
| Regresión v3 con candidata | 100 % | 76,92 % | 100 % | 4136 / 3537 / 2980 | 19063 |

La calidad fue idéntica en las tres repeticiones de cada configuración. La salida estructurada fue del 100 %; no se detectaron fugas, respuestas en casos etiquetados sin respuesta ni extractos inventados o alterados. La peor P95 de la candidata v4 aumenta un 3,56 % frente a la peor P95 de referencia, dentro del +20 % permitido. El arranque frío se mide aparte tras la ingestión: no representa encender todo el sistema desde cero.

Se revisaron las citas de heladas, rosas e inspectores: conservan la condición o excepción completa. En v4-validation-14, la cita identifica a Varo y su intención correctamente, pero el evaluador léxico congelado solo reconoce uno de los dos hechos por la diferencia entre «vertió» y «verter». Se conserva el 95 % calculado, sin corregir a posteriori la puntuación.

Estos resultados proceden de un corpus sintético pequeño y del mismo hardware: Ryzen 7 5800H y RTX 3060 Laptop. Las cifras de v3 sobre el corpus ampliado no son una comparación directa con la ejecución histórica anterior.

## Bloqueos que impiden activar

1. **gm-003 resuelto en las tres repeticiones:** la candidata cita tanto el catálogo público como la fuente privada y cubre los dos hechos.
2. **gm-002 ya supera el gate, pero no responde a la causa.** Los seis pasajes ofrecidos contienen la frase «Este es el motivo…» sin sus antecedentes causales. La respuesta cita esa frase y la observación pública de los mapas: obtiene cero de los dos hechos esperados. Hace falta conservar el contexto explicativo al seleccionar pasajes, no bajar otro umbral.
3. **ia-val-multi omite la cantidad:** Li y sus 12 monedas están entre los pasajes ofrecidos, pero el modelo escoge el permiso de Ámbar y un distractor de la misma fuente que Li. Por eso citar ambas fuentes no acredita cubrir ambos hechos. Falla también el requisito de contexto literal de ese caso.
4. Persisten hechos parciales en gm-004, spoiler-001 y spoiler-002. La ausencia de rechazos respondibles no basta para declarar respuestas completas.

No se ajustaron parámetros para reparar estos fallos tras ver la validación. La próxima iteración debe tratar dependencias causales entre párrafos y cobertura de las partes de la pregunta, manteniendo estos casos como regresiones y reservando nuevas preguntas para validar.

## Verificación y reproducción

- Backend: Maven verify final, 180 pruebas, cero fallos, errores u omisiones; BUILD SUCCESS el 9 de septiembre a las 14:12:47. Registro: backend/target/ia-v4-verify-final.log.
- Frontend: npm run verify, 108 pruebas aprobadas, lint, cobertura y compilación correctos.
- Contratos de decisión: scripts/test-ia-v4-decision.ps1 aprobado; comprueba latencia, repeticiones fallidas, hechos incompletos, ausencia de gm-003, desempate conservador y calibración insegura. La función de aceptación reproduce el bloqueo del informe real.
- Pruebas de integración de recuperación: candidatos fuera del top vectorial, deduplicación, coseno intacto, filtros públicos/privados/revelados, revocación, versiones inactivas y términos vacíos.
- El Ollama dedicado ya se retiró; se conservaron los pesos y los servicios habituales.

Después de la evaluación se completó la telemetría de cobertura recuperada y se hizo explícita la selección de corpus: el runner v4 usa IA_CORPUS_VERSION=4 también en la regresión v3, mientras que el runner histórico mantiene su corpus original. Estos cambios de instrumentación no alteran el tratamiento medido. También se extrajeron las reglas de decisión para probarlas y se endureció el rechazo de informes que omitan casos obligatorios.

Reproducción y controles: [IA-V4.md](../demo/evaluation/IA-V4.md). Los parámetros experimentales siguen siendo LORE_HYBRID_ENABLED=false y QA_SELECTION_PROMPT_VERSION=v1 por defecto.
