# Una identidad propia para Codex of Realms

Investigación de referencias · 16 de septiembre de 2026

## Conclusión

La versión anterior añadió pergamino, latón y una rosa de los vientos, pero mantuvo una cabecera promocional y formularios muy parecidos entre sí. La propuesta es cambiar la composición: dar prioridad a las fichas de campaña y a sus fuentes.

Una ficha con sus relaciones y evidencias al lado permitiría recorrer el mundo y comprobar sus fuentes desde la misma vista.

## Qué prácticas ayudan a conseguir originalidad

### Definir la sensación y el propósito antes de elegir ornamentos

Dan Mall distingue la intención emocional de una pieza de su ejecución técnica. Una página puede estar bien alineada y tener buena tipografía, pero comunicar una personalidad inadecuada. Para Codex conviene elegir primero si estamos construyendo una herramienta de investigación, una experiencia inmersiva o un cuaderno de campaña. [Art Direction and Design, A List Apart](https://alistapart.com/ala317_art_direction_300-png/).

### Reunir referencias con una razón explícita

Los moodboards permiten acordar una dirección mediante imágenes; NN/g recomienda separar conceptos distintos y advierte que un conjunto de capturas de competidores es más bien un tablero de referencias. Este documento es ese primer tablero. Después conviene crear una selección más estrecha de tipografía, ilustración y materiales para cada dirección. [Mood Boards in UX, NN/g](https://www.nngroup.com/articles/mood-boards/).

### Probar muestras pequeñas antes de rehacer la app

Las *style tiles* muestran tipografía, color y algunos elementos reales de interfaz. Permiten discutir el lenguaje visual sin presentar una página completa como si ya fuera definitiva. Para nosotros: un título de personaje, un fragmento citado, una relación y un control de visibilidad. [Samantha Warren: Style Tiles and How They Work](https://alistapart.com/article/style-tiles-and-how-they-work/).

### Dar instrucciones concretas a la IA y criticar su resultado

La guía pública de Anthropic recomienda partir del tema y del contenido, justificar las decisiones y revisar el plan antes de implementar. Identifica como recursos repetidos las frases parcialmente destacadas, las etiquetas superiores en mayúsculas, la numeración sin función y varias combinaciones de color y tipografía. La propuesta anterior de Codex incluía varios de esos recursos. Es una guía práctica, no un estudio que demuestre que una fuente o un color sean intrínsecamente «de IA». [Frontend Design, Anthropic](https://github.com/anthropics/skills/blob/main/skills/frontend-design/SKILL.md).

### Aplicación a nuestro proceso

- Usar contenido real: nombres largos, descripciones, documentos y citas del proyecto.
- Elegir una referencia principal para la estructura y otra para el carácter visual; asignar una función a cada una.
- Introducir una decisión memorable y mantener claros los controles que la rodean.
- Comparar propuestas con la misma ficha y la misma consulta, para juzgar el diseño sin que cambie el contenido.
- Revisar lectura, navegación y sensación general antes de trasladar el estilo a todas las pantallas.

Estos puntos son una síntesis propuesta para Codex, no una receta universal ni una garantía de originalidad.

## Tablero de referencias

Las imágenes de referencia son material de sus respectivos productos y se conservan únicamente para análisis local, excluidas de Git. La documentación pública enlaza a las fuentes originales; no las distribuye como recursos de la aplicación ni como diseños propios. Las observaciones corresponden a la fecha de esta investigación.

### LegendKeeper — el mundo como espacio de navegación

[Referencia visual de LegendKeeper](https://www.legendkeeper.com/example-project)

**Observado:** el proyecto público de ejemplo sitúa el mapa y la navegación del mundo en primer plano. El marco de la aplicación es discreto; nombres, lugares e imágenes aportan carácter. [Proyecto interactivo](https://www.legendkeeper.com/example-project) · [Producto y créditos de ilustración](https://www.legendkeeper.com/).

**Qué tomaría:** navegación persistente, una zona principal de lectura y contexto accesible al lado. Que el nombre del universo y la ficha abierta sean los protagonistas.

**Límite:** un mapa navegable sería una funcionalidad nueva. Podemos aprender de la distribución sin prometer un mapa que todavía no existe.

### Alchemy — una escena como centro visual

[Referencia visual de Alchemy](https://help.alchemyrpg.com/en/articles/9821384-player-orientation)

**Observado:** la captura de su guía de jugador usa una ilustración dominante, controles en los márgenes y paneles que dejan percibir la escena. La ambientación depende mucho del arte y de su encuadre. [Guía oficial con capturas](https://help.alchemyrpg.com/en/articles/9821384-player-orientation).

**Qué tomaría:** una imagen de portada ligada al universo, retratos o lugares con un tratamiento coherente y una interfaz que deje respirar ese contenido.

**Límite:** mantener texto largo encima de ilustraciones perjudicaría la lectura. La ambientación debería concentrarse en portadas o cabeceras de fichas, con superficies tranquilas para documentos. Requeriría imágenes propias o con licencia adecuada.

### Owlbear Rodeo — el contenido ocupa la mesa

[Referencia visual de Owlbear Rodeo](https://www.owlbear.rodeo/)

**Observado:** mapa y fichas ocupan casi toda la superficie; las herramientas se agrupan en barras y zonas pequeñas alrededor. Su documentación define el mapa de combate como foco del producto. [Guía oficial](https://docs.owlbear.rodeo/docs/getting-started/).

**Qué tomaría:** decidir cuál es la tarea principal y darle espacio. Mostrar las acciones secundarias cuando hacen falta.

**Límite:** una mesa de combate tiene necesidades distintas de una biblioteca documental. No trasladaría sus herramientas de juego directamente.

### Kanka — organización del mundo y personalización

**Documentado:** personajes, lugares, diarios y relaciones se organizan como entradas conectadas. Permite adaptar categorías, paneles y temas a una campaña. [Funciones y capturas](https://kanka.io/features#entries) · [Temas](https://kanka.io/features#theming).

**Qué tomaría:** distinguir visualmente los tipos de contenido y permitir que la identidad del universo aparezca en sus fichas. La administración puede ser sobria y seguir perteneciendo al mismo producto.

**Límite:** demasiadas secciones o controles permanentes harían pesada la consulta. Las capturas de fichas no se cargaron de forma fiable durante esta revisión; esta parte de la comparación se apoya en documentación, no en una prueba de uso.

### MÖRK BORG — una voz gráfica reconocible

**Observado:** su portada web combina amarillo intenso, negro y un logotipo gestual de gran tamaño. Es una referencia editorial de rol, no un gestor de campañas. Su sitio también ofrece materiales y una edición de texto sin el tratamiento gráfico del libro. [Sitio oficial](https://morkborg.com/).

**Qué tomaría:** una decisión gráfica firme y consistente puede dar personalidad sin recurrir al repertorio medieval habitual. También podemos buscar referencias en fanzines y libros físicos.

**Límite:** su intensidad y su carácter específico no deberían imponerse a toda una herramienta de lectura. Inspirarse en la contundencia del lenguaje no significa copiar su logotipo ni su paleta.

## Tres direcciones posibles para Codex

| Dirección | Composición | Carácter | Coste o riesgo |
| --- | --- | --- | --- |
| **Archivo de campaña** | Índice lateral, ficha o documento central y evidencias relacionadas a la derecha. | Herramienta de investigación; contenido abundante, controles discretos, identidad en las fichas. | Necesita una buena jerarquía para no acabar pareciendo otra wiki genérica. |
| **Mundo ilustrado** | Portada del universo y páginas de personaje o lugar con imágenes protagonistas. | Inmersivo y evocador; inspirado por la relación entre arte e interfaz de Alchemy. | Depende de un conjunto coherente de imágenes y de una buena solución cuando faltan. |
| **Cuaderno de mesa** | Entradas, notas marginales y referencias cruzadas, con composición editorial. | Más cercano a un fanzine o dossier de campaña que a un panel administrativo. | Puede recaer en el pergamino decorativo si no tiene una voz tipográfica y gráfica específica. |

Son hipótesis de diseño, no maquetas aprobadas. No requieren añadir combate, mapas, cronologías o nuevas funciones para probar el lenguaje visual.

## Recomendación inicial

Empezaría por **Archivo de campaña**, con un tratamiento ilustrado moderado en el contenido cuando dispongamos de imágenes. Es la dirección que mejor conecta con fuentes, canon y relaciones, que ya son el centro de Codex.

La pantalla de prueba debería abrir directamente una ficha del mundo. A la izquierda estaría el índice; en el centro, el contenido; y en el lateral, las fuentes que respaldan lo que se está leyendo. En móvil, ese contexto se abriría mediante una acción explícita. La consulta al archivo tendría su propio espacio o una apertura contextual; la administración de miembros no competiría con la lectura.

Antes de implementar, compararía tres muestras pequeñas y después una pantalla representativa en las dos direcciones preferidas. La elección debe basarse en lo que el usuario reconoce como propio del producto, además de en la facilidad de lectura.

## Alcance y límites

Se han consultado fuentes primarias de diseño, un proyecto público de LegendKeeper, capturas oficiales de Alchemy y Owlbear Rodeo, documentación de Kanka y la portada de MÖRK BORG. No se han creado cuentas ni realizado pruebas completas de estos productos. World Anvil y Obsidian Portal aparecieron en la búsqueda preliminar, pero no se presentan aquí como interfaces inspeccionadas en profundidad.

Esta investigación no modifica el frontend ni da por aceptada ninguna de las tres direcciones.
