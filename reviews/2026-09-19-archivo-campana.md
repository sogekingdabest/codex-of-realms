# Archivo de campaña: integración del diseño aprobado

## Resultado

La interfaz utiliza un índice lateral persistente, fichas de lectura con tipografía serif y fuentes vinculadas en una columna contextual. Atlas, fuentes, consultas y administración tienen espacios separados. Los formularios de edición se abren cuando se necesitan. La paleta combina tonos carbón y un acento rosa tenue.

En móvil, el índice se puede plegar y las fuentes pasan debajo de la ficha. Se conserva el lector original de documentos y el diálogo de evidencias con navegación por teclado.

## Validación

- `npm run verify`: ESLint, 119 pruebas en 15 archivos, cobertura y compilación TypeScript/Vite correctos.
- Cobertura: 85,64 % de sentencias; 78,76 % de ramas; 82,05 % de funciones; 87,3 % de líneas.
- `git diff --check -- frontend`: correcto.
- Revisión en navegador con los componentes reales y una API local de ejemplo: escritorio, ancho de 1024 px y móvil de 320 px.
- Comprobados: selección y cierre de fuentes, índice móvil, edición y cancelación, consultas con citas, foco y cierre por Escape del diálogo, vista de jugador y archivo vacío.
- Formulario y lector sin desbordamiento horizontal a 320 px.

## Alcance de la comprobación

La revisión visual usa datos ficticios en `frontend/test-results/archive-preview/`. La integración completa con Docker/Keycloak y el despliegue quedan fuera de esta comprobación.

Registro de verificación local: `frontend/test-results/archive-verified.log`.
