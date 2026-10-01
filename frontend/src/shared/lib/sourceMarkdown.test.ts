import { describe, expect, it } from 'vitest'

import { locatePassage, parseInline, parseSource, sourceFormat, type InlineRun, type SourceBlock } from './sourceMarkdown'

/** Serialize runs so assertions read like the rendered text. */
function inline(source: string, runs: InlineRun[]) {
  return runs.map((run) => {
    let text = source.slice(run.start, run.end)
    if (run.code) text = `\`${text}\``
    if (run.del) text = `~${text}~`
    if (run.em) text = `<i>${text}</i>`
    if (run.strong) text = `<b>${text}</b>`
    if (run.image) text = `<img ${text}>`
    if ('href' in run) text = `<a ${run.href ?? 'sin-enlace'}>${text}</a>`
    return text
  }).join('')
}

function text(source: string) {
  return inline(source, parseInline(source, 0, source.length))
}

function kinds(blocks: SourceBlock[]) {
  return blocks.map((block) => block.kind)
}

describe('parseSource', () => {
  it('separa metadatos, títulos y párrafos conservando las posiciones originales', () => {
    const source = '---\ntitle: Lumbrevela\nnota sin clave\n---\n\n# El Meridiano #\n\nCiudad al este.\nSegunda línea.\n'
    const blocks = parseSource(source, 'markdown')

    expect(kinds(blocks)).toEqual(['frontMatter', 'heading', 'paragraph'])
    const [frontMatter, heading, paragraph] = blocks
    if (frontMatter.kind !== 'frontMatter' || heading.kind !== 'heading' || paragraph.kind !== 'paragraph') throw new Error('Bloques inesperados')
    expect(frontMatter.entries.map(({ key, value }) => [key && source.slice(key.start, key.end), source.slice(value.start, value.end)]))
      .toEqual([['title', 'Lumbrevela'], [null, 'nota sin clave']])
    expect(heading.level).toBe(1)
    expect(inline(source, heading.inline)).toBe('El Meridiano')
    expect(inline(source, paragraph.inline)).toBe('Ciudad al este.\nSegunda línea.')
    expect(source.slice(paragraph.start, paragraph.end)).toBe('Ciudad al este.\nSegunda línea.')
  })

  it('reconoce títulos subrayados y no confunde una línea de guiones con metadatos', () => {
    const source = 'Crónica\n=======\n\nCapítulo\n---\n\n***\n\n---'
    const blocks = parseSource(source, 'markdown')
    expect(blocks.map((block) => block.kind === 'heading' ? `h${block.level}:${inline(source, block.inline)}` : block.kind))
      .toEqual(['h1:Crónica', 'h2:Capítulo', 'rule', 'rule'])
  })

  it('agrupa listas anidadas, numeradas, tareas y líneas de continuación', () => {
    const source = [
      '- Nara',
      '  - Custodia',
      '    del paso',
      '- [x] Pagada',
      '- [ ] Pendiente',
      '',
      '- Tras un hueco',
      '',
      '3. Tercero',
      '4. Cuarto',
    ].join('\n')
    const [bullets, numbered] = parseSource(source, 'markdown')
    if (bullets.kind !== 'list' || numbered.kind !== 'list') throw new Error('Se esperaban listas')

    expect(bullets.items.map((item) => [item.depth, item.task ?? null, inline(source, item.inline)])).toEqual([
      [0, null, 'Nara'],
      [1, null, 'Custodia\n    del paso'],
      [0, 'done', 'Pagada'],
      [0, 'open', 'Pendiente'],
      [0, null, 'Tras un hueco'],
    ])
    expect(numbered.items.map((item) => [item.ordered, item.number])).toEqual([[true, 3], [true, 4]])
  })

  it('solo interrumpe un párrafo con viñetas o con una lista que empieza en 1', () => {
    const source = 'En el año\n2024. Fue largo.\n- Viñeta\n\nOtro párrafo\n1. Primero'
    expect(kinds(parseSource(source, 'markdown'))).toEqual(['paragraph', 'list', 'paragraph', 'list'])
  })

  it('lee citas con varios párrafos, bloques de código y tablas', () => {
    const source = [
      '> Primera línea',
      '> segunda línea',
      '>',
      '> Otro párrafo',
      '',
      '```md',
      '# no es título',
      '```',
      '',
      '| Nombre | Rol |',
      '| :--- | ---: |',
      '| **Nara** | Guía \\| exploradora |',
      '',
      '~~~',
      'sin cerrar',
    ].join('\n')
    const [quote, code, table, open] = parseSource(source, 'markdown')
    if (quote.kind !== 'quote' || code.kind !== 'code' || table.kind !== 'table' || open.kind !== 'code') throw new Error('Bloques inesperados')

    expect(quote.paragraphs.map((lines) => lines.map((line) => inline(source, line)))).toEqual([['Primera línea', 'segunda línea'], ['Otro párrafo']])
    expect(source.slice(code.content.start, code.content.end)).toBe('# no es título')
    expect(table.header.map((cell) => inline(source, cell))).toEqual(['Nombre', 'Rol'])
    expect(table.rows.map((row) => row.map((cell) => inline(source, cell)))).toEqual([['<b>Nara</b>', 'Guía | exploradora']])
    expect(source.slice(open.content.start, open.content.end)).toBe('sin cerrar')
  })

  it('trata un TXT como texto sin formato, separado por líneas vacías', () => {
    const source = '# Nota\n*literal*\n\n\nSegunda parte'
    expect(sourceFormat('NOTAS.txt')).toBe('text')
    expect(sourceFormat('cronica.md')).toBe('markdown')
    expect(sourceFormat(undefined)).toBe('markdown')
    expect(parseSource(source, 'text').map((block) => source.slice(block.start, block.end))).toEqual(['# Nota\n*literal*', 'Segunda parte'])
  })

  it('no inventa bloques con un documento vacío o sin cierre de metadatos', () => {
    expect(parseSource('', 'markdown')).toEqual([])
    expect(kinds(parseSource('---\ntitle: abierto', 'markdown'))).toEqual(['rule', 'paragraph'])
  })
})

describe('parseInline', () => {
  it.each([
    ['La **deuda** y la *luz*', 'La <b>deuda</b> y la <i>luz</i>'],
    ['***Ambas***', '<b><i>Ambas</i></b>'],
    ['*una **fuerte** frase*', '<i>una </i><b><i>fuerte</i></b><i> frase</i>'],
    ['source_id y _énfasis_', 'source_id y <i>énfasis</i>'],
    ['`**código**` y ~~tachado~~', '`**código**` y ~tachado~'],
    ['\\*literal\\* y 5 * 3 * 2', '*literal* y 5 * 3 * 2'],
    ['**sin cierre y ~30 años', '**sin cierre y ~30 años'],
    ['[mapa](https://example.test/a_(b) "título")', '<a https://example.test/a_(b)>mapa</a>'],
    ['[local](otro.md) y [peligro](javascript:alert(1))', '<a sin-enlace>local</a> y <a sin-enlace>peligro</a>'],
    ['![Mapa de Lumbrevela](mapa.png)', '<img Mapa de Lumbrevela>'],
    ['[vacío]() y [] y [sin](cierre', '<a sin-enlace>vacío</a> y [] y [sin](cierre'],
  ])('%s', (source, expected) => {
    expect(text(source)).toBe(expected)
  })

  it('cada fragmento visible coincide con el texto original', () => {
    const source = 'Nara **guarda** `37` [monedas](https://example.test) y *una _brújula_*.'
    for (const run of parseInline(source, 0, source.length)) {
      expect(source.slice(run.start, run.end).length).toBeGreaterThan(0)
      expect(source.indexOf(source.slice(run.start, run.end), run.start)).toBe(run.start)
    }
  })
})

describe('locatePassage', () => {
  it('ajusta la cita al texto y descarta rangos vacíos', () => {
    expect(locatePassage('abcdef', { start: -4, end: 3 })).toEqual({ start: 0, end: 3 })
    expect(locatePassage('abcdef', { start: 4, end: 99 })).toEqual({ start: 4, end: 6 })
    expect(locatePassage('abcdef', { start: 9, end: 12 })).toBeNull()
    expect(locatePassage('abcdef', null)).toBeNull()
  })
})
