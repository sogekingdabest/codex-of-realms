import { execSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { test, expect, type APIRequestContext, type Browser, type Locator, type Page, type TestInfo } from '@playwright/test'

// Simulated session from docs/product/USER_SESSION_ES.md: one Game Master and two players.
// Each persona acts only through visible names and labels. The record counts interactions
// (clicks, fields, keys) as an effort proxy; seconds are machine time, not human time.
// Run it with `npm run test:session` against compose.e2e.yaml plus compose.session.yaml
// (see OPERATIONS.md). SESSION_COLD=1 unloads both models before the session starts.

const password = 'Test-Only-September-2026!'
const lore = '../demo/lore/'
const suffix = Date.now().toString().slice(-6)

interface TaskRecord {
  id: string; persona: string; role: string; device: string; goal: string
  completed: boolean; seconds: number; interactions: number; notes: string[]
}
const records: TaskRecord[] = []
let shotCount = 0

class Persona {
  interactions = 0
  page!: Page
  constructor(readonly name: string, readonly lastName: string, readonly role: string, readonly device: 'escritorio' | 'móvil') {}
  get email() { return `${this.name.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()}-${suffix}@example.local` }
  get displayName() { return `${this.name} ${this.lastName}` }
  async click(target: Locator) { this.interactions++; await target.click() }
  async fill(target: Locator, value: string) { this.interactions++; await target.fill(value) }
  async select(target: Locator, label: string) { this.interactions++; await target.selectOption({ label }) }
  async upload(target: Locator, file: string | { name: string; mimeType: string; buffer: Buffer }) { this.interactions++; await target.setInputFiles(file) }
  async press(key: string) { this.interactions++; await this.page.keyboard.press(key) }
}

const ines = new Persona('Inés', 'Vidal', 'Dirección', 'escritorio')
const tala = new Persona('Tala', 'Mir', 'Jugadora', 'escritorio')
const ivo = new Persona('Ivo', 'Sanz', 'Jugador', 'móvil')

let info: TestInfo
async function shot(persona: Persona, name: string) {
  shotCount++
  const file = `${String(shotCount).padStart(2, '0')}-${persona.name.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '')}-${name}.png`
  await persona.page.screenshot({ path: info.outputPath(file) })
}

let currentNotes: string[] = []
async function task(id: string, persona: Persona, goal: string, run: (notes: string[]) => Promise<void>) {
  const notes: string[] = []
  currentNotes = notes
  // The moderator closes any dialog left open by a previous task.
  if (persona.page && await persona.page.getByRole('dialog').count()) await persona.page.keyboard.press('Escape')
  const before = persona.interactions
  const started = Date.now()
  let completed = false
  try {
    await run(notes)
    completed = true
  } catch (error) {
    notes.push('FALLO: ' + String(error).split('\n').slice(0, 6).join(' ').slice(0, 600))
    await shot(persona, `${id}-fallo`).catch(() => undefined)
  }
  records.push({ id, persona: persona.name, role: persona.role, device: persona.device, goal, completed,
    seconds: Math.round((Date.now() - started) / 100) / 10, interactions: persona.interactions - before, notes })
  writeFileSync(info.outputPath('session.json'), JSON.stringify(records, null, 2))
  // Every task depends on the prepared campaign.
  if (!completed && id.startsWith('P')) throw new Error(`La preparación ${id} no terminó`)
}

async function register(browser: Browser, request: APIRequestContext, persona: Persona) {
  const context = await browser.newContext(persona.device === 'móvil'
    ? { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2 }
    : { viewport: { width: 1366, height: 860 } })
  const page = await context.newPage()
  persona.page = page
  await page.goto('/')
  const firstContact = persona.role !== 'Dirección'
  if (firstContact) await shot(persona, 'registro-1-acceso')
  await page.getByRole('link', { name: /Register|Registrarse/ }).click()
  await page.locator('#firstName').fill(persona.name)
  await page.locator('#lastName').fill(persona.lastName)
  await page.locator('#email').fill(persona.email)
  if (await page.locator('#password').count()) {
    await page.locator('#password').fill(password)
    await page.locator('#password-confirm').fill(password)
  }
  if (firstContact) await shot(persona, 'registro-2-formulario')
  await page.getByRole('button', { name: /Register|Registrarse/ }).click()
  if (firstContact) await shot(persona, 'registro-3-verifica-correo')
  let messageId = ''
  await expect.poll(async () => {
    const mail = await (await request.get('http://localhost:28025/api/v1/messages')).json()
    messageId = mail.messages.find((m: { To: { Address: string }[] }) => m.To.some(to => to.Address === persona.email))?.ID ?? ''
    return messageId
  }, { timeout: 60_000 }).not.toBe('')
  const mail = await (await request.get('http://localhost:28025/api/v1/message/' + messageId)).json()
  const link = (mail.Text as string).match(/http:\/\/localhost:28180\/[^\s<>]+/)
  await page.goto(link![0].replaceAll('&amp;', '&'))
  const proceed = page.getByRole('link', { name: /proceed|back to application|clic aquí|volver a la aplicación/i })
  if (await proceed.count()) await proceed.first().click()
  await page.locator('#password-new').fill(password)
  await page.locator('#password-confirm').fill(password)
  if (firstContact) await shot(persona, 'registro-4-contrasena')
  await page.getByRole('button', { name: /Submit|Enviar/ }).click()
  await page.goto('/')
  if (await page.locator('#username').count()) {
    await page.locator('#username').fill(persona.email)
    await page.locator('#password').fill(password)
    await page.getByRole('button', { name: /Sign In|Iniciar sesión/ }).click()
  }
  await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible()
}

// Mobile layouts fold the index; open it when the persona needs the list.
async function openIndex(persona: Persona) {
  const toggle = persona.page.getByRole('button', { name: 'Abrir índice' })
  if (await toggle.isVisible()) await persona.click(toggle)
}

async function section(persona: Persona, name: string) {
  const link = persona.page.getByRole('link', { name, exact: true })
  if (!(await link.isVisible())) {
    const toggle = persona.page.getByRole('button', { name: 'Abrir índice' })
    if (await toggle.isVisible()) {
      currentNotes.push(`Para ir a «${name}» tuvo que pulsar antes «Abrir índice»: la navegación estaba plegada.`)
      await persona.click(toggle)
    }
  }
  await persona.click(persona.page.getByRole('link', { name, exact: true }))
}

async function uploadSource(persona: Persona, file: string, title: string, visibility: string) {
  const page = persona.page
  const form = page.getByRole('button', { name: 'Subir y procesar' })
  if (!(await form.isVisible())) await persona.click(page.getByText('Añadir conocimiento', { exact: true }))
  await persona.fill(page.getByLabel('Título', { exact: true }), title)
  await persona.select(page.getByRole('combobox', { name: 'Visibilidad', exact: true }), visibility)
  await persona.upload(page.getByLabel('Archivo Markdown o TXT'), lore + file)
  await persona.click(page.getByRole('button', { name: 'Subir y procesar' }))
  // The library lists a source only once it is published; the job panel shows it meanwhile.
  await expect(page.getByRole('region', { name: 'Procesamiento de fuentes' }).getByRole('heading', { name: new RegExp(`^${title}`) }).first()).toBeVisible({ timeout: 60_000 })
}

async function waitForProcessing(persona: Persona, finished: number) {
  await expect(persona.page.getByText(`Operaciones finalizadas (${finished})`, { exact: true })).toBeVisible({ timeout: 600_000 })
}

async function createEntity(persona: Persona, entity: { type: string; name: string; description: string; visibility: string; source: string; chunk: string }) {
  const page = persona.page
  await persona.click(page.getByRole('button', { name: 'Nueva ficha' }))
  await persona.select(page.getByRole('combobox', { name: 'Tipo', exact: true }), entity.type)
  await persona.fill(page.getByLabel('Nombre', { exact: true }), entity.name)
  await persona.fill(page.getByLabel('Descripción', { exact: true }), entity.description)
  await persona.select(page.getByRole('combobox', { name: 'Visibilidad de la afirmación', exact: true }), entity.visibility)
  await persona.select(page.getByRole('combobox', { name: 'Fuente', exact: true }), entity.source)
  await persona.click(page.getByRole('checkbox', { name: `Seleccionar ${entity.chunk}` }))
  await persona.click(page.getByRole('button', { name: 'Crear como propuesta' }))
  await expect(page.getByRole('heading', { name: entity.name, level: 2 })).toBeVisible()
}

// Leak probe: none of these texts may appear in the persona's library, atlas or relations.
async function assertHidden(persona: Persona, texts: string[], notes: string[]) {
  const page = persona.page
  for (const [area, open] of [
    ['Fuentes', async () => { await section(persona, 'Fuentes'); await openIndex(persona) }],
    ['Fichas', async () => { await section(persona, 'Atlas del canon'); await openIndex(persona) }],
    ['Relaciones', async () => { await section(persona, 'Atlas del canon'); await persona.click(page.getByRole('link', { name: 'Relaciones', exact: true })); await openIndex(persona) }],
  ] as const) {
    await open()
    await page.waitForLoadState('networkidle')
    for (const text of texts) await expect(page.locator('body'), `${persona.name} no debe ver «${text}» en ${area}`).not.toContainText(text)
  }
  notes.push(`Sin rastro de ${texts.map((text) => `«${text}»`).join(', ')} en Fuentes, Fichas y Relaciones.`)
}

async function ask(persona: Persona, question: string, notes: string[]) {
  const page = persona.page
  await section(persona, 'Consultas')
  await persona.fill(page.getByLabel('¿Qué quieres saber?'), question)
  const started = Date.now()
  await persona.click(page.getByRole('button', { name: 'Consultar', exact: true }))
  await expect(page.getByRole('button', { name: 'Consultar', exact: true })).toBeEnabled({ timeout: 300_000 })
  const seconds = Math.round((Date.now() - started) / 100) / 10
  // Whether the result starts inside the viewport the persona is looking at.
  // The first heading of the result must be fully on screen, not just its top edge.
  const top = await page.locator('.answer-region h3, .answer-region p').first()
    .evaluate((element) => ({ bottom: Math.round(element.getBoundingClientRect().bottom), height: window.innerHeight }))
    .catch(() => null)
  const where = top && top.bottom <= top.height
    ? 'visible sin desplazarse'
    : `fuera de la pantalla: empieza en ${top?.bottom ?? '?'} px con una ventana de ${top?.height ?? '?'} px`
  const toast = page.getByRole('alert')
  if (await toast.count()) {
    const text = (await toast.first().innerText()).replaceAll('\n', ' · ').replace('×', '').trim()
    notes.push(`«${question}» → ERROR tras ${seconds} s: «${text}»`)
    await toast.first().getByRole('button', { name: 'Cerrar aviso' }).click()
    return { answered: false, error: true, seconds, citations: [] as string[] }
  }
  const insufficient = page.locator('article.insufficient')
  if (await insufficient.count()) {
    notes.push(`«${question}» → sin respuesta verificable en ${seconds} s (${where}): ${(await insufficient.innerText()).replaceAll('\n', ' · ')}`)
    return { answered: false, error: false, seconds, citations: [] as string[] }
  }
  const citations = await page.locator('.citations li strong').allInnerTexts()
  const quote = (await page.locator('blockquote').allInnerTexts()).join(' / ').slice(0, 300)
  notes.push(`«${question}» → respuesta en ${seconds} s (${where}) citando ${citations.join(', ') || 'nada'}. Pasaje: «${quote}»`)
  return { answered: true, error: false, seconds, citations }
}

test('sesión simulada: dirección y dos jugadores', async ({ browser, request }, testInfo) => {
  info = testInfo
  if (process.env.SESSION_COLD === '1') {
    // Start as after a reboot: no model in memory.
    for (const model of ['qwen3.5:4b', 'bge-m3']) execSync(`docker exec codex-session-ollama-1 ollama stop ${model}`, { stdio: 'ignore' })
  }
  const realmName = 'El Meridiano de Ceniza'
  const publicSources = [
    ['public/01-el-meridiano-y-lumbrevela.md', 'El Meridiano y la ciudad de Lumbrevela'],
    ['public/02-personas-facciones-y-objetos.md', 'Personas, facciones y objetos de Lumbrevela'],
    ['public/03-rutas-y-vida-civica.md', 'Rutas y vida cívica de Lumbrevela'],
  ] as const
  let gmSourceUrl = ''

  // Preparation from DEMO.md, done by the Game Master before the group arrives.
  await task('P1', ines, 'Preparar la campaña: universo, tres fuentes públicas y dos invitaciones', async (notes) => {
    await register(browser, request, ines)
    const page = ines.page
    await shot(ines, 'inicio-sin-universo')
    await ines.fill(page.getByLabel('Nombre del universo'), realmName)
    await ines.click(page.getByRole('button', { name: 'Crear universo' }))
    await section(ines, 'Fuentes')
    const uploaded = Date.now()
    for (const [file, title] of publicSources) await uploadSource(ines, file, title, 'Público · Pública')
    await shot(ines, 'fuentes-procesando')
    notes.push('Mientras procesa: ' + (await page.locator('main').innerText()).replaceAll('\n', ' · ').slice(0, 240))
    await waitForProcessing(ines, 3)
    notes.push(`Tres fuentes públicas publicadas ${Math.round((Date.now() - uploaded) / 1000)} s después de empezar a subirlas (primer uso del modelo de embeddings).`)
    await shot(ines, 'fuentes-publicadas')
    await section(ines, 'Personas y permisos')
    for (const player of [tala, ivo]) {
      await ines.fill(page.getByLabel('Correo del jugador'), player.email)
      await ines.click(page.getByRole('button', { name: 'Invitar', exact: true }))
      await expect(page.getByText(player.email).first()).toBeVisible()
    }
    notes.push('Invitaciones creadas; la aplicación no envía correo, el enlace se comparte aparte.')
  })
  await task('P2', tala, 'Registrarse con la invitación y entrar en la campaña', async () => {
    await register(browser, request, tala)
    await expect(tala.page.getByText(realmName).first()).toBeVisible()
    await shot(tala, 'primera-entrada')
  })
  await task('P3', ivo, 'Registrarse desde el móvil con la invitación', async () => {
    await register(browser, request, ivo)
    await expect(ivo.page.getByText(realmName).first()).toBeVisible()
    await shot(ivo, 'primera-entrada-movil')
  })
  await task('P4', ines, 'Preparar spoiler revelado solo a Tala, nota privada y Atlas', async (notes) => {
    const page = ines.page
    await page.reload()
    await section(ines, 'Personas y permisos')
    await ines.fill(page.getByLabel('Nombre', { exact: true }), 'Recuerdos de Nara')
    await ines.click(page.getByRole('button', { name: 'Crear grupo' }))
    const group = page.locator('.spoiler-list article', { hasText: 'Recuerdos de Nara' })
    await ines.click(group.getByRole('checkbox', { name: tala.displayName }))
    await expect(group.getByRole('checkbox', { name: tala.displayName })).toBeChecked()
    await section(ines, 'Fuentes')
    await uploadSource(ines, 'spoilers/01-el-recuerdo-de-nara.md', 'El recuerdo de Nara', 'Recuerdos de Nara · Spoiler con permiso')
    await uploadSource(ines, 'gm-only/01-la-deuda-de-la-aguja.md', 'La deuda de la Aguja', 'Solo dirección · Solo dirección')
    await waitForProcessing(ines, 5)
    await ines.click(page.getByRole('link', { name: 'Leer La deuda de la Aguja' }))
    gmSourceUrl = new URL(page.url()).pathname
    await shot(ines, 'nota-privada-abierta')
    await section(ines, 'Atlas del canon')
    await createEntity(ines, { type: 'Lugar', name: 'Lumbrevela', description: 'Ciudad al este del Meridiano, sobre siete terrazas de roca rojiza.', visibility: 'Público', source: publicSources[0][1], chunk: 'Lumbrevela' })
    await ines.click(page.getByRole('button', { name: 'Promover a canon' }))
    await expect(page.getByRole('button', { name: 'Promover a canon' })).toHaveCount(0)
    await createEntity(ines, { type: 'Objeto', name: 'La Aguja del Mediodía', description: 'Faro sin costa en la terraza superior de Lumbrevela.', visibility: 'Público', source: publicSources[0][1], chunk: 'Lumbrevela' })
    await createEntity(ines, { type: 'Personaje', name: 'Nara Ors', description: 'Hermana mayor de Maela, atrapada en el Meridiano.', visibility: 'Recuerdos de Nara', source: 'El recuerdo de Nara', chunk: 'La hermana olvidada' })
    await ines.click(page.getByRole('link', { name: 'Relaciones', exact: true }))
    await ines.click(page.getByRole('button', { name: 'Nueva relación' }))
    await ines.select(page.getByRole('combobox', { name: 'Origen', exact: true }), 'La Aguja del Mediodía')
    await ines.select(page.getByRole('combobox', { name: 'Destino', exact: true }), 'Lumbrevela')
    await ines.fill(page.getByLabel('Tipo de relación'), 'Se encuentra en')
    await ines.fill(page.getByLabel('Descripción', { exact: true }), 'La Aguja ocupa el centro de la terraza superior.')
    await ines.select(page.getByRole('combobox', { name: 'Visibilidad de la afirmación', exact: true }), 'Público')
    await ines.click(page.getByRole('checkbox', { name: 'Seleccionar Lumbrevela' }))
    await ines.click(page.getByRole('button', { name: 'Crear como propuesta' }))
    await expect(page.getByText('Se encuentra en', { exact: false }).first()).toBeVisible()
    await shot(ines, 'atlas-relacion')
    notes.push(`Nota privada en ${gmSourceUrl}`)
  })
  await task('P5', ines, 'Calentar el modelo con la consulta de control', async (notes) => {
    let result = await ask(ines, '¿Dónde se alza Lumbrevela?', notes)
    await shot(ines, 'consulta-control')
    // As the guide says, the Game Master retries before the players arrive.
    for (let attempt = 2; !result.answered && attempt <= 3; attempt++) {
      notes.push(`Intento ${attempt} de la consulta de control.`)
      result = await ask(ines, '¿Dónde se alza Lumbrevela?', notes)
    }
    expect(result.answered).toBe(true)
  })

  // Tasks for the group, one at a time.
  await task('T1', tala, 'Encontrar dónde se alza Lumbrevela y mostrar el texto original (empieza por Consultas)', async (notes) => {
    const page = tala.page
    await page.goto('/')
    await shot(tala, 't1-punto-de-partida')
    const result = await ask(tala, '¿Dónde se alza Lumbrevela?', notes)
    expect(result.answered).toBe(true)
    await shot(tala, 't1-respuesta')
    await tala.click(page.getByRole('button', { name: /Abrir contexto \[1\]/ }))
    const dialog = page.getByRole('dialog')
    await expect(dialog.locator('mark').first()).toBeVisible()
    notes.push(`Pasaje marcado: «${(await dialog.locator('mark').first().innerText()).slice(0, 160)}»`)
    await shot(tala, 't1-evidencia')
    await tala.click(page.getByRole('button', { name: 'Leer documento completo' }))
    await expect(dialog.getByRole('heading', { name: 'Lumbrevela', exact: true })).toBeVisible()
    await shot(tala, 't1-documento')
    await tala.press('Escape')
  })
  await task('T1', ivo, 'Encontrar dónde se alza Lumbrevela y mostrar el texto original (empieza por el Atlas, en móvil)', async (notes) => {
    const page = ivo.page
    await page.goto('/')
    await shot(ivo, 't1-punto-de-partida')
    await section(ivo, 'Atlas del canon')
    await openIndex(ivo)
    await shot(ivo, 't1-indice-atlas')
    await ivo.click(page.getByRole('button', { name: /Lumbrevela/ }).or(page.getByRole('link', { name: /ficha de Lumbrevela/ })).first())
    await expect(page.getByRole('heading', { name: 'Lumbrevela', level: 2 })).toBeVisible()
    await shot(ivo, 't1-ficha')
    const evidence = page.getByRole('complementary', { name: 'Fuentes de la ficha' })
    await evidence.scrollIntoViewIfNeeded()
    notes.push(`Fragmento visible en la ficha: «${(await evidence.locator('blockquote').innerText()).slice(0, 160)}»`)
    await shot(ivo, 't1-fuentes-de-la-ficha')
    await ivo.click(evidence.getByRole('button').first())
    await expect.poll(async () => (await page.getByRole('dialog').locator('mark').allInnerTexts()).join(' ')).toContain('Lumbrevela se alza')
    const header = await page.getByRole('dialog').locator('header').innerText()
    notes.push(`Cabecera del diálogo de evidencia: «${header.replaceAll('\n', ' · ').replace('×', '').trim()}»`)
    await shot(ivo, 't1-evidencia-movil')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await ivo.press('Escape')
  })
  await task('T2', ivo, 'Buscar un documento por su nombre y leerlo sin consultar a la IA', async (notes) => {
    const page = ivo.page
    await section(ivo, 'Fuentes')
    await openIndex(ivo)
    await ivo.fill(page.getByLabel('Buscar fuentes'), 'rutas')
    notes.push(`Resultados al buscar «rutas»: ${(await page.locator('.source-search small').innerText())}`)
    await shot(ivo, 't2-busqueda')
    await ivo.click(page.getByRole('link', { name: 'Leer Rutas y vida cívica de Lumbrevela' }))
    const reader = page.getByRole('article', { name: 'Fuente: Rutas y vida cívica de Lumbrevela' })
    await expect(reader.getByRole('heading', { name: 'La Baliza de Sal' })).toBeVisible()
    await shot(ivo, 't2-lectura')
    await ivo.click(reader.getByRole('button', { name: 'Cerrar fuente' }))
  })
  await task('T3', ines, 'Añadir una nota privada y revelar un spoiler a Ivo', async (notes) => {
    const page = ines.page
    await section(ines, 'Personas y permisos')
    await ines.fill(page.getByLabel('Nombre', { exact: true }), 'La campana de vidrio')
    await ines.click(page.getByRole('button', { name: 'Crear grupo' }))
    const group = page.locator('.spoiler-list article', { hasText: 'La campana de vidrio' })
    await ines.click(group.getByRole('checkbox', { name: ivo.displayName }))
    await expect(group.getByRole('checkbox', { name: ivo.displayName })).toBeChecked()
    await shot(ines, 't3-grupos')
    await section(ines, 'Fuentes')
    await uploadSource(ines, 'gm-only/02-el-pacto-del-velo.md', 'El pacto del Velo', 'Solo dirección · Solo dirección')
    await uploadSource(ines, 'spoilers/02-la-campana-de-vidrio.md', 'La campana de vidrio', 'La campana de vidrio · Spoiler con permiso')
    await waitForProcessing(ines, 7)
    await openIndex(ines)
    await shot(ines, 't3-biblioteca-direccion')
    notes.push('Marcas en la biblioteca de dirección: ' + (await page.locator('.sources-panel .visibility-mark').allInnerTexts()).join(' | '))
  })
  await task('T3', tala, 'Comprobar qué información puede ver (tiene Recuerdos de Nara)', async (notes) => {
    const page = tala.page
    await page.reload()
    await section(tala, 'Fuentes')
    await openIndex(tala)
    const titles = await page.locator('.sources-panel .source-index-link span').allInnerTexts()
    notes.push(`Fuentes visibles: ${titles.join(', ')}`)
    notes.push('Marcas: ' + (await page.locator('.sources-panel .visibility-mark').allInnerTexts()).join(' | '))
    expect(titles).toContain('El recuerdo de Nara')
    await shot(tala, 't3-biblioteca')
    await assertHidden(tala, ['La deuda de la Aguja', 'El pacto del Velo', 'La campana de vidrio'], notes)
    await section(tala, 'Atlas del canon')
    await openIndex(tala)
    await expect(page.getByText('Nara Ors').first()).toBeVisible()
    await shot(tala, 't3-atlas-con-spoiler')
    await page.goto(gmSourceUrl)
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).not.toContainText('La deuda de la Aguja')
    notes.push(`URL directa de la nota privada: «${(await page.locator('main').innerText()).replaceAll('\n', ' · ').slice(0, 200)}»`)
    await shot(tala, 't3-url-directa-privada')
  })
  await task('T3', ivo, 'Comprobar qué información puede ver (tiene La campana de vidrio)', async (notes) => {
    const page = ivo.page
    await page.reload()
    await section(ivo, 'Fuentes')
    await openIndex(ivo)
    const titles = await page.locator('.sources-panel .source-index-link span').allInnerTexts()
    notes.push(`Fuentes visibles: ${titles.join(', ')}`)
    expect(titles).toContain('La campana de vidrio')
    await shot(ivo, 't3-biblioteca-movil')
    await assertHidden(ivo, ['La deuda de la Aguja', 'El pacto del Velo', 'El recuerdo de Nara', 'Nara Ors'], notes)
    const probe = await ask(ivo, '¿Quién es Nara Ors y dónde está?', notes)
    expect(probe.citations).not.toContain('El recuerdo de Nara')
    await expect(page.locator('main')).not.toContainText('hermana mayor')
    const gm = await ask(ivo, '¿Avanza realmente el Meridiano hacia el oeste?', notes)
    expect(gm.citations).not.toContain('La deuda de la Aguja')
    await shot(ivo, 't3-consulta-sin-filtracion')
  })
  await task('T4', ines, 'Sustituir una nota por una versión nueva y explicar cuál está disponible', async (notes) => {
    const page = ines.page
    await section(ines, 'Fuentes')
    await openIndex(ines)
    const item = page.locator('.sources-panel article.source-item', { has: page.getByRole('link', { name: 'Leer Rutas y vida cívica de Lumbrevela' }) })
    await ines.click(item.getByText('Gestionar', { exact: true }))
    const replacement = Buffer.from(readFileSync(lore + 'public/03-rutas-y-vida-civica.md', 'utf8')
      + '\n\n## Nueva baliza\n\nDesde el mes de Cenit de 83 DP, la Custodia coloca una segunda Baliza de Sal en el paso norte.\n')
    await ines.upload(page.getByLabel('Reemplazar archivo de Rutas y vida cívica de Lumbrevela'), { name: '03-rutas-y-vida-civica.md', mimeType: 'text/markdown', buffer: replacement })
    await page.waitForTimeout(400)
    await shot(ines, 't4-procesando')
    notes.push('Mientras procesa: ' + (await page.locator('.source-jobs').innerText()).replaceAll('\n', ' · ').slice(0, 400))
    notes.push('Entrada de la biblioteca: ' + (await item.innerText()).replaceAll('\n', ' · ').slice(0, 200))
    await waitForProcessing(ines, 8)
    await expect(item).toContainText('v2')
    await shot(ines, 't4-version-nueva')
  })
  await task('T5', tala, 'Abrir una ficha del Atlas y seguir una relación hasta otra ficha', async (notes) => {
    const page = tala.page
    await section(tala, 'Atlas del canon')
    await openIndex(tala)
    await shot(tala, 't5-atlas')
    await tala.click(page.getByRole('button', { name: /La Aguja del Mediodía/ }).or(page.getByRole('link', { name: /ficha de La Aguja del Mediodía/ })).first())
    await expect(page.getByRole('heading', { name: 'La Aguja del Mediodía', level: 2 })).toBeVisible()
    const relations = page.getByRole('region', { name: 'Relaciones de la ficha' })
    notes.push('Relación mostrada: ' + (await relations.innerText()).replaceAll('\n', ' · ').slice(0, 200))
    await shot(tala, 't5-ficha-aguja')
    await tala.click(relations.getByRole('link', { name: 'Ver ficha de Lumbrevela' }))
    await expect(page.getByRole('heading', { name: 'Lumbrevela', level: 2 })).toBeVisible()
    notes.push(`URL de llegada: ${new URL(page.url()).pathname}`)
    await shot(tala, 't5-ficha-lumbrevela')
  })
  await task('T6', ines, 'Crear otra campaña y volver a la primera', async (notes) => {
    const page = ines.page
    await ines.click(page.getByRole('button', { name: 'Nuevo universo', exact: true }))
    await ines.fill(page.getByLabel('Nombre del universo'), 'Las Islas de Sal')
    await ines.click(page.getByRole('button', { name: 'Crear universo' }))
    await expect(page.getByRole('heading', { name: 'Las Islas de Sal', level: 1 })).toBeVisible()
    notes.push('Al crearla abre: ' + (await page.locator('main').innerText()).replaceAll('\n', ' · ').slice(0, 200))
    await shot(ines, 't6-segunda-campana')
    const switcher = page.getByLabel('Universo activo')
    if (!(await switcher.isVisible())) {
      notes.push('El selector de universo está plegado bajo «Propietario · Cambiar universo».')
      await ines.click(page.getByText(/Cambiar universo/))
    }
    await ines.select(switcher, realmName)
    await expect(page.getByRole('heading', { name: realmName, level: 1 })).toBeVisible()
    notes.push(`Vuelve a ${new URL(page.url()).pathname}`)
    await shot(ines, 't6-vuelta')
  })
  await task('T7', ivo, 'Preguntar quién fundó la universidad de Aramonte (dato ausente) y explicar el resultado', async (notes) => {
    const result = await ask(ivo, '¿Quién fundó la universidad de Aramonte?', notes)
    await shot(ivo, 't7-resultado')
    expect(result.answered, 'el archivo no contiene ese dato').toBe(false)
    expect(result.error, 'una abstención no es un error').toBe(false)
  })

  for (const persona of [ines, tala, ivo]) await persona.page?.context().close()
  expect(records.filter((record) => !record.completed).map((record) => `${record.id} ${record.persona}`)).toEqual([])
})
