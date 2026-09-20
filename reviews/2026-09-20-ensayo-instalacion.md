# Ensayo de instalación y primera sesión

Revisión del commit `022c5f7`, descargado desde GitHub en una copia temporal. El ensayo usa una base de datos y un almacén de documentos nuevos, separados de la instalación habitual.

## Comprobaciones terminadas

- [CI del commit publicado](https://github.com/sogekingdabest/codex-of-realms/actions/runs/35517061408): backend, frontend, Compose y los dos escenarios de navegador correctos. Los escenarios de CI usan modelos simulados.
- Compilación y arranque del Compose normal desde la copia limpia, con contraseñas nuevas y servicios saludables.
- Registro y verificación de tres cuentas ficticias mediante Keycloak y Mailpit; aceptación de las invitaciones de dos jugadores.
- Ingestión de tres fuentes de la demo con `bge-m3` real, ejecutado en GPU; lectura de los originales en el navegador.
- Fuente pública visible para ambos jugadores, fuente privada oculta para ambos y spoiler visible para uno solo.
- Creación de una ficha del Atlas y promoción a canon.

Estas comprobaciones se realizaron con un guion de navegador. Todavía no se ha observado a jugadores externos usando el producto.

## Problemas encontrados

La descarga de `bge-m3` se bloqueó por tiempos de espera del servidor de archivos. Para continuar se copiaron los pesos ya disponibles a un volumen nuevo, montando el original solo en lectura. Por tanto, el ensayo no acredita una descarga completa desde cero. Después se aplicó el override GPU documentado.

Ollama devuelve el nombre `bge-m3:latest`, mientras que la configuración pide `bge-m3`. La comparación literal provocaba un aviso de modelo ausente aunque la ingestión funcionara. La corrección trata esos dos nombres como equivalentes y sigue distinguiendo las versiones explícitas. Las pruebas incluyen el alias en ambos sentidos y un registro con puerto.

La primera consulta de ubicación superó los tres minutos de espera del guion. Ollama registró una petición de chat de 3 minutos y 10 segundos y el backend registró un timeout de lectura. La creación de cuentas, los permisos, las fuentes y el Atlas habían terminado antes de ese fallo. No se considera aprobado el recorrido completo por haber pasado esas etapas.

La repetición aislada de «¿Dónde se alza Lumbrevela?» devolvió `ANSWERED`, HTTP 200 y el pasaje correcto con su cita en ambas peticiones:

| Petición | Tiempo completo observado |
|---|---:|
| Primera, sin modelos cargados al comenzar | 65,154 s |
| Segunda, inmediatamente después | 3,383 s |

Son dos observaciones de una misma pregunta, no una evaluación de calidad ni un benchmark. La consulta funciona, pero la carga inicial varía lo suficiente como para preparar los modelos antes de la demo. El guion completo original quedó fallido; la repetición acredita únicamente la consulta aislada.

## Verificación de la corrección

`mvnw.cmd --batch-mode --no-transfer-progress verify` terminó con `BUILD SUCCESS`: 190 pruebas, cero fallos, errores u omisiones. Incluye cinco casos nuevos para la equivalencia de `latest`. El frontend no cambia. Esta corrección se verificó con la suite de backend; las consultas de navegador anteriores corresponden al commit publicado, sin el arreglo.

Los servicios del ensayo se detuvieron al terminar. Sus volúmenes se conservaron; los datos de la instalación habitual no se modificaron.

## Preparación de la sesión con jugadores

El [guion en español](../docs/product/USER_SESSION_ES.md) incluye tareas y una hoja de observación. Antes de convocar al grupo hay que acordar si se usará un ordenador compartido, varios equipos en red local o acceso por Internet. La configuración de `localhost` no permite resolver los dos últimos casos cambiando únicamente la URL.

La restauración de backups y una evaluación completa de calidad de modelos quedan fuera de este ensayo.
