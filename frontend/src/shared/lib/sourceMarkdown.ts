/**
 * Lightweight Markdown reader for campaign sources.
 *
 * Citations and catalogue evidence point at character offsets of the original text, so every
 * block and inline run keeps its [start, end) range over the untouched source. Syntax markers are
 * simply left out of the runs; the visible text is always `source.slice(run.start, run.end)`.
 */
export interface TextRange {
  readonly start: number
  readonly end: number
}

export interface InlineRun extends TextRange {
  readonly strong?: boolean
  readonly em?: boolean
  readonly del?: boolean
  readonly code?: boolean
  readonly image?: boolean
  readonly href?: string
}

export interface ListItem extends TextRange {
  readonly depth: number
  readonly ordered: boolean
  readonly number: number
  readonly task?: 'open' | 'done'
  readonly inline: InlineRun[]
}

export interface FrontMatterEntry {
  readonly key: TextRange | null
  readonly value: TextRange
}

export type SourceBlock = TextRange & (
  | { readonly kind: 'frontMatter'; readonly entries: FrontMatterEntry[] }
  | { readonly kind: 'heading'; readonly level: number; readonly inline: InlineRun[] }
  | { readonly kind: 'paragraph'; readonly inline: InlineRun[] }
  | { readonly kind: 'plain' }
  | { readonly kind: 'quote'; readonly paragraphs: InlineRun[][][] }
  | { readonly kind: 'list'; readonly items: ListItem[] }
  | { readonly kind: 'code'; readonly content: TextRange }
  | { readonly kind: 'rule' }
  | { readonly kind: 'table'; readonly header: InlineRun[][]; readonly rows: InlineRun[][][] }
)

export type SourceFormat = 'markdown' | 'text'

export function sourceFormat(filename: string | null | undefined): SourceFormat {
  return /\.txt$/i.test(filename ?? '') ? 'text' : 'markdown'
}

export function parseSource(source: string, format: SourceFormat): SourceBlock[] {
  return format === 'text' ? parsePlainText(source) : parseMarkdown(source)
}

interface Line extends TextRange {
  readonly next: number
}

const FENCE = /^( {0,3})(`{3,}|~{3,})(.*)$/
const ATX = /^ {0,3}(#{1,6})(?=[ \t]|$)/
const RULE = /^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$/
const QUOTE = /^ {0,3}(?:>[ \t]?)+/
const LIST = /^([ \t]*)([-*+]|\d{1,9}[.)])(?:[ \t]+|$)/
const TABLE_DELIMITER = /^[ \t]*\|?[ \t]*:?-+:?[ \t]*(?:\|[ \t]*:?-+:?[ \t]*)*\|?[ \t]*$/
const SETEXT = /^ {0,3}(=+|-+)[ \t]*$/
const FRONT_MATTER_OPEN = /^---[ \t]*$/
const FRONT_MATTER_CLOSE = /^(?:---|\.\.\.)[ \t]*$/
const FRONT_MATTER_ENTRY = /^([\w.-]+)[ \t]*:[ \t]*/
const PUNCTUATION = /[!-/:-@[-`{-~]/
const WORD = /[\p{L}\p{N}]/u
const SPACE = /\s/

function splitLines(source: string): Line[] {
  const lines: Line[] = []
  let start = 0
  for (;;) {
    const newline = source.indexOf('\n', start)
    const stop = newline === -1 ? source.length : newline
    const end = stop > start && source[stop - 1] === '\r' ? stop - 1 : stop
    lines.push({ start, end, next: stop + 1 })
    if (newline === -1) return lines
    start = newline + 1
  }
}

function parsePlainText(source: string): SourceBlock[] {
  const blocks: SourceBlock[] = []
  let start = -1
  let end = -1
  for (const line of splitLines(source)) {
    if (!isBlank(source, line)) {
      if (start < 0) start = line.start
      end = line.end
    } else if (start >= 0) {
      blocks.push({ kind: 'plain', start, end })
      start = -1
    }
  }
  if (start >= 0) blocks.push({ kind: 'plain', start, end })
  return blocks
}

function parseMarkdown(source: string): SourceBlock[] {
  const lines = splitLines(source)
  const text = (index: number) => source.slice(lines[index].start, lines[index].end)
  const blocks: SourceBlock[] = []
  let index = 0

  const frontMatter = readFrontMatter(source, lines, text)
  if (frontMatter) {
    blocks.push(frontMatter.block)
    index = frontMatter.nextLine
  }

  while (index < lines.length) {
    const line = lines[index]
    const content = text(index)
    if (isBlank(source, line)) {
      index++
      continue
    }
    const fence = FENCE.exec(content)
    if (fence && !(fence[2][0] === '`' && fence[3].includes('`'))) {
      const marker = fence[2]
      let close = index + 1
      while (close < lines.length && !isFenceClose(text(close), marker)) close++
      const closed = close < lines.length
      const last = closed ? close - 1 : lines.length - 1
      const contentStart = Math.min(line.next, source.length)
      const contentEnd = last > index ? lines[last].end : contentStart
      blocks.push({
        kind: 'code',
        start: line.start,
        end: closed ? lines[close].end : lines[last].end,
        content: { start: contentStart, end: Math.max(contentStart, contentEnd) },
      })
      index = closed ? close + 1 : lines.length
      continue
    }
    const heading = ATX.exec(content)
    if (heading) {
      blocks.push(readAtxHeading(source, line, heading[0].length, heading[1].length))
      index++
      continue
    }
    if (RULE.test(content)) {
      blocks.push({ kind: 'rule', start: line.start, end: line.end })
      index++
      continue
    }
    if (QUOTE.test(content)) {
      index = readQuote(source, lines, index, text, blocks)
      continue
    }
    if (content.includes('|') && index + 1 < lines.length && isTableDelimiter(text(index + 1))) {
      index = readTable(source, lines, index, text, blocks)
      continue
    }
    if (LIST.test(content)) {
      index = readList(source, lines, index, text, blocks)
      continue
    }
    index = readParagraph(source, lines, index, text, blocks)
  }
  return blocks
}

function readFrontMatter(source: string, lines: Line[], text: (index: number) => string) {
  if (lines.length < 2 || !FRONT_MATTER_OPEN.test(text(0))) return null
  let close = 1
  while (close < lines.length && !FRONT_MATTER_CLOSE.test(text(close))) close++
  if (close >= lines.length) return null
  const entries: FrontMatterEntry[] = []
  for (let index = 1; index < close; index++) {
    const line = lines[index]
    if (isBlank(source, line)) continue
    const entry = FRONT_MATTER_ENTRY.exec(text(index))
    entries.push(entry
      ? { key: { start: line.start, end: line.start + entry[1].length }, value: trimRange(source, line.start + entry[0].length, line.end) }
      : { key: null, value: trimRange(source, line.start, line.end) })
  }
  return { block: { kind: 'frontMatter', start: 0, end: lines[close].end, entries } satisfies SourceBlock, nextLine: close + 1 }
}

function readAtxHeading(source: string, line: Line, markerLength: number, level: number): SourceBlock {
  const content = trimRange(source, line.start + markerLength, line.end)
  let end = content.end
  let hashes = end
  while (hashes > content.start && source[hashes - 1] === '#') hashes--
  if (hashes < end && (hashes === content.start || /[ \t]/.test(source[hashes - 1]))) {
    end = trimRange(source, content.start, hashes).end
  }
  return { kind: 'heading', level, start: line.start, end: line.end, inline: parseInline(source, content.start, end) }
}

function readQuote(source: string, lines: Line[], first: number, text: (index: number) => string, blocks: SourceBlock[]) {
  const paragraphs: InlineRun[][][] = []
  let current: InlineRun[][] = []
  let index = first
  while (index < lines.length) {
    const marker = QUOTE.exec(text(index))
    if (!marker) break
    const content = trimRange(source, lines[index].start + marker[0].length, lines[index].end)
    if (content.end > content.start) {
      current.push(parseInline(source, content.start, content.end))
    } else if (current.length > 0) {
      paragraphs.push(current)
      current = []
    }
    index++
  }
  if (current.length > 0) paragraphs.push(current)
  blocks.push({ kind: 'quote', start: lines[first].start, end: lines[index - 1].end, paragraphs })
  return index
}

function readTable(source: string, lines: Line[], first: number, text: (index: number) => string, blocks: SourceBlock[]) {
  const header = tableCells(source, lines[first])
  const rows: InlineRun[][][] = []
  let index = first + 2
  while (index < lines.length && !isBlank(source, lines[index]) && text(index).includes('|')) {
    rows.push(tableCells(source, lines[index]))
    index++
  }
  blocks.push({ kind: 'table', start: lines[first].start, end: lines[index - 1].end, header, rows })
  return index
}

function tableCells(source: string, line: Line): InlineRun[][] {
  const edges: number[] = []
  for (let position = line.start; position < line.end; position++) {
    if (source[position] === '\\') position++
    else if (source[position] === '|') edges.push(position)
  }
  const bounded = trimRange(source, line.start, line.end)
  const leading = edges[0] === bounded.start
  const trailing = edges.length > (leading ? 1 : 0) && edges[edges.length - 1] === bounded.end - 1
  const inner = edges.slice(leading ? 1 : 0, trailing ? -1 : undefined)
  const cuts = [leading ? edges[0] : line.start - 1, ...inner, trailing ? edges[edges.length - 1] : line.end]
  const cells: InlineRun[][] = []
  for (let position = 0; position + 1 < cuts.length; position++) {
    const cell = trimRange(source, cuts[position] + 1, cuts[position + 1])
    cells.push(parseInline(source, cell.start, cell.end))
  }
  return cells
}

function readList(source: string, lines: Line[], first: number, text: (index: number) => string, blocks: SourceBlock[]) {
  const items: (Omit<ListItem, 'inline' | 'end'> & { contentStart: number; end: number })[] = []
  const indents: number[] = []
  let index = first
  let previousBlank = false
  while (index < lines.length) {
    const line = lines[index]
    const content = text(index)
    const item = RULE.test(content) ? null : LIST.exec(content)
    if (item) {
      const indent = indentWidth(item[1])
      const ordered = /\d/.test(item[2])
      if (items.length > 0 && indent <= indents[0] && ordered !== items[0].ordered) break
      while (indents.length > 0 && indent < indents[indents.length - 1]) indents.pop()
      if (indents.length === 0 || indent > indents[indents.length - 1]) indents.push(indent)
      let contentStart = line.start + item[0].length
      const task = /^\[([ xX])\][ \t]/.exec(source.slice(contentStart, line.end))
      if (task) contentStart += task[0].length
      items.push({
        start: line.start,
        end: line.end,
        contentStart,
        depth: indents.length - 1,
        ordered,
        number: ordered ? Number.parseInt(item[2], 10) : 1,
        task: task ? (task[1] === ' ' ? 'open' : 'done') : undefined,
      })
      previousBlank = false
    } else if (isBlank(source, line)) {
      const following = nextFilledLine(source, lines, index)
      if (following < 0) break
      const next = text(following)
      if (!LIST.test(next) && !/^[ \t]{2,}/.test(next)) break
      previousBlank = true
    } else if (previousBlank ? /^[ \t]{2,}/.test(content) : !startsBlock(content)) {
      items[items.length - 1].end = line.end
      previousBlank = false
    } else {
      break
    }
    index++
  }
  const last = items[items.length - 1]
  blocks.push({
    kind: 'list',
    start: lines[first].start,
    end: last.end,
    items: items.map(({ contentStart, ...item }) => {
      const content = trimRange(source, contentStart, item.end)
      return { ...item, inline: parseInline(source, content.start, content.end) }
    }),
  })
  return index
}

function readParagraph(source: string, lines: Line[], first: number, text: (index: number) => string, blocks: SourceBlock[]) {
  let index = first
  while (index < lines.length) {
    if (index > first) {
      const content = text(index)
      const setext = SETEXT.exec(content)
      if (setext) {
        const range = trimRange(source, lines[first].start, lines[index - 1].end)
        blocks.push({
          kind: 'heading',
          level: setext[1][0] === '=' ? 1 : 2,
          start: lines[first].start,
          end: lines[index].end,
          inline: parseInline(source, range.start, range.end),
        })
        return index + 1
      }
      if (isBlank(source, lines[index]) || interruptsParagraph(content)) break
    }
    index++
  }
  const range = trimRange(source, lines[first].start, lines[index - 1].end)
  blocks.push({ kind: 'paragraph', ...range, inline: parseInline(source, range.start, range.end) })
  return index
}

function startsBlock(content: string) {
  return FENCE.test(content) || ATX.test(content) || RULE.test(content) || QUOTE.test(content) || LIST.test(content)
}

function interruptsParagraph(content: string) {
  const item = LIST.exec(content)
  if (item) return !/\d/.test(item[2]) || Number.parseInt(item[2], 10) === 1
  return FENCE.test(content) || ATX.test(content) || RULE.test(content) || QUOTE.test(content)
}

function isFenceClose(content: string, marker: string) {
  const close = /^ {0,3}(`{3,}|~{3,})[ \t]*$/.exec(content)
  return close !== null && close[1][0] === marker[0] && close[1].length >= marker.length
}

function isTableDelimiter(content: string) {
  return content.includes('|') && TABLE_DELIMITER.test(content)
}

function nextFilledLine(source: string, lines: Line[], from: number) {
  for (let index = from + 1; index < lines.length; index++) {
    if (!isBlank(source, lines[index])) return index
  }
  return -1
}

function indentWidth(indent: string) {
  let width = 0
  for (const character of indent) width += character === '\t' ? 4 : 1
  return width
}

function isBlank(source: string, range: TextRange) {
  for (let position = range.start; position < range.end; position++) {
    if (!SPACE.test(source[position])) return false
  }
  return true
}

function trimRange(source: string, start: number, end: number): TextRange {
  let from = start
  let to = end
  while (from < to && SPACE.test(source[from])) from++
  while (to > from && SPACE.test(source[to - 1])) to--
  return { start: from, end: to }
}

type Marks = Omit<InlineRun, 'start' | 'end'>

export function parseInline(source: string, start: number, end: number, marks: Marks = {}): InlineRun[] {
  const runs: InlineRun[] = []
  let textStart = start
  let position = start
  const flush = (to: number) => {
    if (to > textStart) runs.push({ start: textStart, end: to, ...marks })
  }
  const take = (from: number, parsed: InlineRun[], resume: number) => {
    flush(from)
    runs.push(...parsed)
    position = resume
    textStart = resume
  }

  while (position < end) {
    const character = source[position]
    if (character === '\\' && position + 1 < end && PUNCTUATION.test(source[position + 1])) {
      flush(position)
      textStart = position + 1
      position += 2
      continue
    }
    if (character === '`') {
      const length = runLength(source, position, end, '`')
      const close = findCodeClose(source, position + length, end, length)
      if (close >= 0) {
        const code = close > position + length ? [{ start: position + length, end: close, ...marks, code: true }] : []
        take(position, code, close + length)
      } else {
        position += length
      }
      continue
    }
    if (character === '[' || (character === '!' && source[position + 1] === '[')) {
      const image = character === '!'
      const link = parseLink(source, image ? position + 1 : position, end)
      if (link) {
        const linkMarks = image ? { ...marks, image: true } : { ...marks, href: safeHref(link.url) }
        take(position, parseInline(source, link.textStart, link.textEnd, linkMarks), link.next)
        continue
      }
    }
    if (character === '*' || character === '_' || character === '~') {
      const emphasis = parseEmphasis(source, position, end, character)
      if (emphasis) {
        const emphasisMarks: Marks = { ...marks, [emphasis.mark]: true }
        take(position, parseInline(source, emphasis.innerStart, emphasis.innerEnd, emphasisMarks), emphasis.next)
      } else {
        position += runLength(source, position, end, character)
      }
      continue
    }
    position++
  }
  flush(end)
  return runs
}

function parseEmphasis(source: string, position: number, end: number, character: string) {
  const length = runLength(source, position, end, character)
  const sizes = character === '~' ? (length === 2 ? [2] : []) : length >= 2 ? [2, 1] : [1]
  if (character === '_' && position > 0 && WORD.test(source[position - 1])) return null
  for (const size of sizes) {
    const innerStart = position + size
    if (innerStart >= end || SPACE.test(source[innerStart])) continue
    const close = findEmphasisClose(source, innerStart, end, character, size)
    if (close > innerStart) {
      const mark = character === '~' ? 'del' : size === 2 ? 'strong' : 'em'
      return { innerStart, innerEnd: close, next: close + size, mark } as const
    }
  }
  return null
}

function findEmphasisClose(source: string, from: number, end: number, character: string, size: number) {
  for (let position = from; position < end; position++) {
    const current = source[position]
    if (current === '\\') {
      position++
      continue
    }
    if (current === '`') {
      const length = runLength(source, position, end, '`')
      const close = findCodeClose(source, position + length, end, length)
      position = close >= 0 ? close + length - 1 : position + length - 1
      continue
    }
    if (current !== character) continue
    const length = runLength(source, position, end, character)
    const rightFlanking = !SPACE.test(source[position - 1])
    const closesWord = character !== '_' || position + length >= end || !WORD.test(source[position + length])
    if (rightFlanking && closesWord && length >= size && !(size === 1 && length === 2)) return position + length - size
    position += length - 1
  }
  return -1
}

function parseLink(source: string, open: number, end: number) {
  let depth = 0
  let close = open
  for (; close < end; close++) {
    const current = source[close]
    if (current === '\\') close++
    else if (current === '[') depth++
    else if (current === ']' && --depth === 0) break
  }
  if (close >= end || source[close + 1] !== '(' || close === open + 1) return null
  let parentheses = 0
  let target = close + 2
  for (; target < end; target++) {
    const current = source[target]
    if (current === '\\') target++
    else if (current === '\n') return null
    else if (current === '(') parentheses++
    else if (current === ')' && parentheses-- === 0) break
  }
  if (target >= end) return null
  const url = source.slice(close + 2, target).trim().split(/\s+/)[0].replace(/^<(.*)>$/, '$1')
  return { textStart: open + 1, textEnd: close, url, next: target + 1 }
}

function safeHref(url: string) {
  return /^(?:https?:\/\/|mailto:)/i.test(url) ? url : undefined
}

function findCodeClose(source: string, from: number, end: number, length: number) {
  for (let position = from; position < end; position++) {
    if (source[position] !== '`') continue
    const run = runLength(source, position, end, '`')
    if (run === length) return position
    position += run - 1
  }
  return -1
}

function runLength(source: string, position: number, end: number, character: string) {
  let length = 0
  while (position + length < end && source[position + length] === character) length++
  return length
}

/** Clamp a citation to the text; `null` when nothing of it can be located. */
export function locatePassage(source: string, range: TextRange | null | undefined): TextRange | null {
  if (!range) return null
  const start = Math.max(0, Math.min(range.start, source.length))
  const end = Math.max(start, Math.min(range.end, source.length))
  return end > start ? { start, end } : null
}

export function intersects(first: TextRange, second: TextRange) {
  return first.start < second.end && second.start < first.end
}
