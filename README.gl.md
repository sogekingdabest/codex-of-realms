# Codex of Realms

[![CI](https://github.com/sogekingdabest/codex-of-realms/actions/workflows/ci.yml/badge.svg)](https://github.com/sogekingdabest/codex-of-realms/actions/workflows/ci.yml)
[![Versión](https://img.shields.io/github/v/release/sogekingdabest/codex-of-realms?include_prereleases&label=versi%C3%B3n)](https://github.com/sogekingdabest/codex-of-realms/releases)
[![Licenza: MIT](https://img.shields.io/badge/licenza-MIT-blue)](LICENSE)

[English](README.md) · [Español](README.es.md) · **Galego**

Un arquivo compartido para campañas de rol de mesa. Reúne notas de sesión, personaxes e lugares, e permite que cada xogador vexa só o que lle toca saber.

Por exemplo, quen dirixe a partida pode gardar en privado a verdade sobre unha torre antiga, compartir a súa historia pública con todo o grupo e revelarlle unha pista a un só xogador. Cada persoa pode percorrer as súas fontes ou facer unha pregunta e abrir as pasaxes nas que se basea a resposta.

A interface está en castelán. Todo funciona no teu ordenador, tamén os modelos de linguaxe. A primeira beta é a [0.1.0-beta](CHANGELOG.md#010-beta--2-october-2026). A documentación detallada está en inglés.

## Que podes facer

- Subir e ler ficheiros Markdown ou de texto, buscar por título ou nome de ficheiro e substituír un documento mentres a súa versión anterior segue dispoñible.
- Organizar personaxes, lugares, faccións, obxectos e sucesos no **Atlas del canon**, con relacións e referencias ás fontes.
- Convidar editores e xogadores, manter en privado as notas da dirección e revelar spoilers a quen ti escollas.
- Facer preguntas sobre as fontes publicadas e consultar o texto orixinal de cada cita.

O atlas mantense a man. As preguntas usan os documentos subidos; editar unha ficha do atlas non cambia eses documentos.

## Velo en funcionamento

![Lumbrevela no atlas da campaña, cunha relación e a súa fonte ao carón](demo/assets/atlas.png)

[Ver o percorrido breve](demo/assets/recorrido.webm) · [Presentación de cinco minutos e capturas](demo/PORTFOLIO.md)

Capturado o 20 de setembro cunha versión anterior da interface, datos dunha campaña ficticia e modelos locais. O percorrido gravouse cos modelos xa cargados.

## Execútao no teu ordenador

Necesitas:

- Docker con Compose.
- Uns 16 GB de RAM e espazo para as imaxes dos contedores e uns 5 GB de modelos. Recoméndase unha GPU NVIDIA de 6 GB; sen ela, os modelos usan a CPU e as respostas tardan máis.
- PowerShell para cargar a campaña de exemplo (`pwsh` en Linux e macOS).

1. Copia `.env.example` a `.env` e pon os tres contrasinais.
2. Desde a raíz do repositorio, arrinca os servizos e descarga os modelos. As descargas fanse unha vez por volume de Ollama.

   ```sh
   docker compose up -d --build --wait
   docker compose exec ollama ollama pull bge-m3
   docker compose exec ollama ollama pull qwen3.5:4b
   ```

   Cunha GPU NVIDIA, arríncaos con `docker compose -f compose.yaml -f compose.gpu.yaml up -d --build --wait`.
3. Carga a campaña de exemplo:

   ```sh
   ./scripts/load-demo-campaign.ps1
   ```

   Crea El Meridiano de Ceniza con tres contas e mostra o seu contrasinal: Inés dirixe a partida, Tala coñece un spoiler e Oren só ve o canon público.
4. Abre [a aplicación](http://localhost:5173) e entra. Usa unha xanela privada para cada conta e compara o que ve cada unha.

Se prefires comezar a túa propia campaña, rexístrate na aplicación e confirma o teu correo en [Mailpit](http://localhost:8025), a caixa de correo local. Despois crea un universo e sube as túas notas. Se abriches a aplicación antes de que rematasen as descargas, recárgaa.

A [guía da demo](docs/operations/DEMO.md) inclúe un percorrido de oito minutos. [Desenvolvemento local](docs/operations/LOCAL_DEVELOPMENT.md) explica os portos e como resolver problemas, e as instalacións existentes deben seguir os [pasos de actualización](OPERATIONS.md#upgrade-an-existing-installation). Para deter os servizos, usa `docker compose down`; os teus datos quedan nos volumes de Docker.

## Como funciona

React e TypeScript forman o cliente. Unha aplicación Spring Boot con Java 21 xestiona os permisos, a inxestión de documentos e as preguntas. PostgreSQL garda os datos da campaña e os embeddings de pgvector; Keycloak xestiona o acceso e Ollama executa os modelos.

O backend filtra as fontes por permisos antes de ordenar os resultados da busca. O modelo escolle as pasaxes e o servidor copia o texto orixinal na resposta. O procesamento de documentos usa traballos persistentes, así que o que falla pódese reintentar.

A [arquitectura](docs/architecture/ARCHITECTURE.md) explica o porqué destas decisións.

## Comprobacións

Con Java 21 e Docker dispoñibles, executa desde `backend/`:

```sh
./mvnw --batch-mode --no-transfer-progress verify
```

En Windows, usa `.\mvnw.cmd`. Con Node.js 24, executa desde `frontend/`:

```sh
npm ci
npm run verify
```

Estas ordes executan as probas do backend e, no frontend, lint, probas, cobertura e compilación. As probas de navegador, a recuperación de copias e a avaliación de modelos teñen [instrucións á parte](OPERATIONS.md#reproducible-acceptance).

## Limitacións

A instalación incluída é só para uso local. Pódense subir ficheiros Markdown e de texto de ata 1 MiB. Unha pasaxe citada pode ser exacta e, aínda así, incompleta; por iso o lector de fontes segue dispoñible para comprobar as respostas. A primeira resposta despois de arrincar pode tardar arredor dun minuto mentres se cargan os modelos. A lista completa está en [limitacións actuais](docs/product/LIMITATIONS.md).

[Documentación](docs/README.md) · [Folla de ruta](ROADMAP.md) · [Cambios](CHANGELOG.md) · [Como contribuír](CONTRIBUTING.md) · [Seguridade](SECURITY.md)

## Licenza

[MIT](LICENSE). A campaña de demostración é material orixinal do proxecto. As dependencias conservan as súas propias licenzas.
