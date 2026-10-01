import { Fragment, useEffect, useMemo, useRef, type ReactNode } from 'react'

import {
  intersects,
  locatePassage,
  parseSource,
  type InlineRun,
  type ListItem,
  type SourceBlock,
  type SourceFormat,
  type TextRange,
} from '../lib/sourceMarkdown'

/**
 * - `document`: the whole text, with the passage marked and scrolled into view.
 * - `context`: only the blocks around the passage, with the passage marked.
 * - `passage`: only the passage itself, without marks.
 */
export type SourceTextDisplay = 'document' | 'context' | 'passage'

interface RenderContext {
  readonly source: string
  readonly clip: TextRange | null
  readonly mark: TextRange | null
  readonly headingLevel: number
}

type Gap = { readonly kind: 'gap'; readonly start: number }

export function SourceText({ content, format = 'markdown', passage = null, display = 'document', headingLevel = 2 }: Readonly<{
  content: string
  format?: SourceFormat
  passage?: TextRange | null
  display?: SourceTextDisplay
  /** Level of the heading the text sits under; a Markdown `#` renders one level below it. */
  headingLevel?: number
}>) {
  const blocks = useMemo(() => parseSource(content, format), [content, format])
  const container = useRef<HTMLDivElement>(null)
  const range = locatePassage(content, passage)
  const context: RenderContext = {
    source: content,
    clip: display === 'passage' ? range : null,
    mark: display === 'passage' ? null : range,
    headingLevel,
  }
  const markStart = context.mark?.start ?? -1
  const markEnd = context.mark?.end ?? -1

  useEffect(() => {
    if (markStart < 0) return
    container.current?.querySelector('mark')?.scrollIntoView?.({ block: 'center' })
  }, [blocks, markStart, markEnd, display])

  return (
    <div ref={container} className="source-text">
      {visibleBlocks(blocks, range, display).map((block) => block.kind === 'gap'
        ? <p key={`gap-${block.start}`} className="source-gap"><span aria-hidden="true">…</span><span className="visually-hidden">Texto omitido</span></p>
        : renderBlock(block, context))}
    </div>
  )
}

function visibleBlocks(blocks: SourceBlock[], range: TextRange | null, display: SourceTextDisplay): (SourceBlock | Gap)[] {
  if (display === 'passage') return range ? blocks.filter((block) => intersects(block, range)) : []
  if (display === 'document' || !range) return blocks
  const cited = blocks.flatMap((block, index) => intersects(block, range) ? [index] : [])
  if (cited.length === 0) return blocks
  let first = cited[0]
  let last = cited[cited.length - 1]
  if (first > 0 && blocks[first - 1].kind !== 'frontMatter') first--
  if (last < blocks.length - 1) last++
  const omittedBefore = blocks.slice(0, first).some((block) => block.kind !== 'frontMatter')
  return [
    ...(omittedBefore ? [{ kind: 'gap', start: blocks[first].start - 1 } as const] : []),
    ...blocks.slice(first, last + 1),
    ...(last < blocks.length - 1 ? [{ kind: 'gap', start: blocks[last].end + 1 } as const] : []),
  ]
}

function renderBlock(block: SourceBlock, context: RenderContext): ReactNode {
  const key = `${block.kind}-${block.start}`
  const className = context.mark && intersects(block, context.mark) ? 'is-cited' : undefined
  switch (block.kind) {
    case 'frontMatter': {
      if (context.clip) return null
      return (
        <details key={key} className={classes('source-front-matter', className)} open={className !== undefined}>
          <summary>Metadatos del documento</summary>
          <dl>
            {block.entries.map((entry) => (
              <div key={entry.value.start}>
                {entry.key && <dt>{context.source.slice(entry.key.start, entry.key.end)}</dt>}
                <dd>{renderRuns([entry.value], context)}</dd>
              </div>
            ))}
          </dl>
        </details>
      )
    }
    case 'heading': {
      const children = renderRuns(block.inline, context)
      if (children.length === 0) return null
      const Heading = `h${Math.min(6, context.headingLevel + block.level)}` as 'h3'
      return <Heading key={key} className={classes('source-heading', `source-heading-${block.level}`, className)}>{children}</Heading>
    }
    case 'paragraph':
    case 'plain': {
      const children = renderRuns(block.kind === 'plain' ? [block] : block.inline, context)
      if (children.length === 0) return null
      return <p key={key} className={classes(block.kind === 'plain' ? 'source-plain' : undefined, className)}>{children}</p>
    }
    case 'quote': {
      const paragraphs = block.paragraphs
        .map((lines) => lines.map((line) => renderRuns(line, context)).filter((line) => line.length > 0))
        .filter((lines) => lines.length > 0)
      if (paragraphs.length === 0) return null
      return (
        <blockquote key={key} className={className}>
          {paragraphs.map((lines, index) => <p key={index}>{lines.flatMap((line, lineIndex) => lineIndex > 0 ? ['\n', ...line] : line)}</p>)}
        </blockquote>
      )
    }
    case 'list': {
      const entries = block.items
        .map((item) => ({ item, children: renderRuns(item.inline, context) }))
        .filter(({ children }) => children.length > 0 || !context.clip)
      if (entries.length === 0) return null
      return <Fragment key={key}>{renderListLevel(nestItems(entries), className)}</Fragment>
    }
    case 'code': {
      const children = renderRuns([block.content], context)
      if (children.length === 0 && context.clip) return null
      return <pre key={key} className={className}><code>{children}</code></pre>
    }
    case 'rule':
      if (context.clip && !(block.start >= context.clip.start && block.end <= context.clip.end)) return null
      return <hr key={key} className={className} />
    case 'table': {
      const header = block.header.map((cell) => renderRuns(cell, context))
      const rows = block.rows
        .map((row) => row.map((cell) => renderRuns(cell, context)))
        .filter((row) => !context.clip || row.some((cell) => cell.length > 0))
      const showHeader = !context.clip || header.some((cell) => cell.length > 0)
      if (!showHeader && rows.length === 0) return null
      return (
        <div key={key} className={classes('source-table', className)}>
          <table>
            {showHeader && <thead><tr>{header.map((cell, index) => <th key={index}>{cell}</th>)}</tr></thead>}
            <tbody>{rows.map((row, index) => <tr key={index}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>)}</tbody>
          </table>
        </div>
      )
    }
  }
}

interface ListNode {
  readonly item: ListItem
  readonly children: ReactNode[]
  readonly nested: ListNode[]
}

function nestItems(entries: { item: ListItem; children: ReactNode[] }[]): ListNode[] {
  const root: ListNode[] = []
  const stack: { depth: number; nodes: ListNode[] }[] = [{ depth: -1, nodes: root }]
  for (const entry of entries) {
    while (stack[stack.length - 1].depth >= entry.item.depth) stack.pop()
    const node: ListNode = { ...entry, nested: [] }
    stack[stack.length - 1].nodes.push(node)
    stack.push({ depth: entry.item.depth, nodes: node.nested })
  }
  return root
}

function renderListLevel(nodes: ListNode[], className?: string): ReactNode[] {
  const groups: ListNode[][] = []
  for (const node of nodes) {
    const group = groups[groups.length - 1]
    if (group && group[0].item.ordered === node.item.ordered) group.push(node)
    else groups.push([node])
  }
  return groups.map((group) => {
    const first = group[0].item
    const List = first.ordered ? 'ol' : 'ul'
    return (
      <List key={first.start} className={className} start={first.ordered && first.number !== 1 ? first.number : undefined}>
        {group.map(({ item, children, nested }) => (
          <li key={item.start} className={item.task ? 'source-task' : undefined}>
            {item.task && <input type="checkbox" checked={item.task === 'done'} disabled readOnly />}
            {children}
            {nested.length > 0 && renderListLevel(nested)}
          </li>
        ))}
      </List>
    )
  })
}

interface Piece {
  readonly run: InlineRun
  readonly start: number
  readonly end: number
  readonly marked: boolean
}

function renderRuns(runs: readonly InlineRun[], context: RenderContext): ReactNode[] {
  const pieces: Piece[] = []
  for (const run of runs) {
    const start = context.clip ? Math.max(run.start, context.clip.start) : run.start
    const end = context.clip ? Math.min(run.end, context.clip.end) : run.end
    if (end <= start) continue
    const cuts = [start, end]
    const { mark } = context
    if (mark) cuts.push(...[mark.start, mark.end].filter((edge) => edge > start && edge < end))
    cuts.sort((first, second) => first - second)
    for (let index = 0; index + 1 < cuts.length; index++) {
      const pieceStart = cuts[index]
      const pieceEnd = cuts[index + 1]
      pieces.push({ run, start: pieceStart, end: pieceEnd, marked: mark !== null && pieceStart >= mark.start && pieceEnd <= mark.end })
    }
  }

  const nodes: ReactNode[] = []
  for (let index = 0; index < pieces.length;) {
    const { href } = pieces[index].run
    if (!href) {
      nodes.push(renderPiece(pieces[index], context.source))
      index++
      continue
    }
    const link: Piece[] = []
    while (index < pieces.length && pieces[index].run.href === href) link.push(pieces[index++])
    nodes.push(
      <a key={`link-${link[0].start}`} href={href} target="_blank" rel="noopener noreferrer">
        {link.map((piece) => renderPiece(piece, context.source))}
      </a>,
    )
  }
  return nodes
}

function renderPiece({ run, start, end, marked }: Piece, source: string): ReactNode {
  let node: ReactNode = source.slice(start, end)
  if (marked) node = <mark>{node}</mark>
  if (run.code) node = <code>{node}</code>
  if (run.del) node = <del>{node}</del>
  if (run.em) node = <em>{node}</em>
  if (run.strong) node = <strong>{node}</strong>
  if (run.image) node = <span className="source-image">{node}</span>
  return <Fragment key={`${start}-${end}`}>{node}</Fragment>
}

function classes(...names: (string | undefined)[]) {
  const value = names.filter(Boolean).join(' ')
  return value || undefined
}
