# Recuperación complementaria y selección completa (v4)

## Contratos y configuración

La búsqueda combina dos vías sobre fragmentos autorizados: búsqueda vectorial con similitud coseno mínima 0,45 y búsqueda de texto completo en español con PostgreSQL.

Cada vía aporta hasta diez resultados. Se fusionan con RRF (pesos iguales, constante 60), se deduplican por fragmento y se retienen diez. Los empates se resuelven por UUID de fragmento. A partir de esos resultados se construyen los seis pasajes con la selección existente.

La similitud coseno, los rangos vectorial/léxico, los términos coincidentes/ausentes y la puntuación RRF son señales distintas. El gate inicial admite similitud suficiente o coincidencia léxica de al menos dos términos distintos en un fragmento con cobertura mínima calibrada. La cobertura de los pasajes sigue siendo 0,70. No se infiere corrección semántica de estas coincidencias.

- LORE_HYBRID_ENABLED=false: vía complementaria desactivada por defecto hasta aceptación.
- LORE_LEXICAL_MINIMUM_COVERAGE=1.0: cobertura de lexemas españoles, candidatos 1,00 / 0,90 / 0,80.
- QA_SELECTION_PROMPT_VERSION=v1: referencia; v2 pide cubrir todas las partes, causas, condiciones y excepciones, y recibe sourceId además del identificador de pasaje.

No cambian el JSON público de respuesta, el modelo Qwen3.5:4b, bge-m3, los tres extractos, 6.000 caracteres ni 8.192 tokens. RetrievalSignals se excluye del JSON público y se registra explícitamente en los informes internos. No hay migración ni regeneración de embeddings. La búsqueda léxica se calcula al consultar; su coste debe medirse en corpus mayores antes de añadir índices.

## Reproducción

Se necesitan Java 21, Docker con GPU y los pesos existentes de Qwen y bge-m3. Usar el Compose aislado descrito en [IA.md](IA.md); no se descargan modelos ni se gestiona la memoria de una instancia habitual.

Desde la raíz, con el endpoint dedicado ya iniciado:

~~~powershell
.\scripts\evaluate-ia-v4.ps1 -OllamaBaseUrl http://127.0.0.1:11435 -DedicatedEndpoint
~~~

El runner comprueba los hashes de ia-v4.freeze.json, registra hardware/modelos/código, audita correspondencia de todos los vectores almacenados frente a embeddings individuales y ejecuta:

1. Calibración de referencia (vector-v1, prompt-v1).
2. Seis variantes: hybrid-v1 con prompt-v1/v2 y cobertura léxica 1,00/0,90/0,80.
3. Congelación de configuración antes de validar: reducir rechazos sin nuevas respuestas indebidas ni regresión de hechos/exactitud; desempatar por mayor umbral y después prompt-v2.
4. Validación: tres repeticiones de referencia y candidato sobre el mismo corpus.
5. Tres repeticiones de regresión v3, exigiendo gm-002, gm-003 con ambas fuentes y todos los casos obligatorios.

V4 tiene 16 casos de calibración (canal/reloj) y 24 de validación (jardín/mina), con fuentes públicas, privadas y reveladas. V3 queda como regresión de desarrollo; no se usa para calibrar. Todos los corpus se ingieren en ambos tratamientos, incluidos los distractores. El corpus es sintético y pequeño; evaluar calidad general y rendimiento requiere fuentes más variadas y numerosas.

## Aceptación y diagnóstico

Cada repetición debe cumplir exactitud ≥80 %, hechos ≥70 %, estructura ≥95 %, citas ≥95 %, cero fugas y cero respuestas indebidas, además del contexto obligatorio. El candidato no puede reducir calidad frente a la referencia y su peor P95 por repetición no puede superar +20 %. Se mide la petición completa; embedding, consulta SQL, expansión y modelo tienen tiempos separados. La primera petición fría se registra aparte, después de la ingestión, y no equivale al arranque desde apagado de todo el sistema.

El informe conserva el motivo interno del gate (LOW_SIMILARITY o LOW_QUESTION_COVERAGE), cobertura y términos de los pasajes, señales de ambas vías, IDs ofrecidos/elegidos y literalidad. El API conserva LOW_RELEVANCE para ambos rechazos. El evaluador TextTerms de hechos no cambia; la métrica de estructura exige ahora un outcome válido, para no contar un borrador vacío como JSON correcto.

Los informes detallados quedan en demo/evaluation/results/ia-v4-*. El runner nunca activa la función: una decisión elegible requiere revisar contexto y aplicar explícitamente los valores por defecto. Si falla calibración, validación, auditoría o regresión, se conserva el bloqueo sin ajustar sobre validación.

## Resultado de la primera ejecución

La evaluación terminó con ACCEPTANCE_BLOCKED. V4 supera los mínimos y la latencia, pero la regresión falla en gm-002 e ia-val-multi. Se mantienen los valores por defecto desactivados. Véase la [revisión de entrega](../../reviews/2026-09-09-recuperacion-complementaria.md).

El runner fija IA_CORPUS_VERSION=4 para incluir los originales v4 también en la regresión v3. El runner histórico v3 conserva su corpus anterior. Los contratos de decisión pueden verificarse con scripts/test-ia-v4-decision.ps1.

La siguiente iteración sobre contexto y omisiones está documentada en [IA-V5.md](IA-V5.md).
