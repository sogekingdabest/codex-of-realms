# Identidad visual: un archivo de mundos

## Dirección

La propuesta usa fondos oscuros, detalles de latón y superficies claras para leer y consultar. La cabecera incluye cartografía vectorial original y la navegación toma la forma de un índice de capítulos. El emblema y el favicon comparten silueta.

La biblioteca conserva una superficie oscura y las consultas ganan protagonismo con una hoja clara, líneas de escritura y una cinta de lectura. El atlas, la administración y los estados vacíos comparten colores y jerarquía. Los estados de disponibilidad mantienen su distinción respecto de los acentos decorativos.

Los recursos son locales; no se añaden dependencias, fuentes remotas ni peticiones externas. La capa visual está en `frontend/src/shared/styles/chronicle.css`, junto con los tokens de `foundation.css`. Las modificaciones de React son de presentación y navegación accesible.

## Validación

- `npm run verify`: 113 pruebas, lint, cobertura y compilación correctos. Cobertura: 85,83 % de sentencias, 78,46 % de ramas, 82,58 % de funciones y 87,32 % de líneas.
- Chromium con la aplicación React y API simulada: archivo, respuesta con citas, atlas, universo vacío y rol jugador.
- Sin desbordamiento horizontal a 320, 390, 768 y 1024 px.
- Lector: foco inicial en cierre, Escape y devolución del foco al enlace de origen.
- Inspección de capturas de escritorio y móvil. Ajuste del contraste del lector y de los estados hover sobre papel.
- Se conserva la preferencia de movimiento reducido y se añade un enlace para saltar al contenido.

Las comprobaciones visuales usan datos de prueba y una API simulada. La integración con autenticación, backend y modelos, así como el despliegue en Docker, queda fuera de esta revisión.

## Capturas

- [Archivo en escritorio](assets/2026-09-15-chronicle/archive-desktop.png)
- [Archivo en móvil](assets/2026-09-15-chronicle/mobile-archive.png)
- [Respuesta con citas](assets/2026-09-15-chronicle/answer-desktop.png)
- [Atlas](assets/2026-09-15-chronicle/atlas-desktop.png)
- [Lectura móvil](assets/2026-09-15-chronicle/reader-mobile.png)
- [Primer universo](assets/2026-09-15-chronicle/empty-desktop.png)
