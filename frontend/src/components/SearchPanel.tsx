import { useState, type ReactNode } from 'react'
import {
  DeepFindApiError,
  openFile,
  revealFile,
  type SearchResponse,
  type SearchFilters,
  type SearchResult,
  type SearchSnippet,
} from '../api/deepfindApi'

interface SearchPanelProps {
  query: string
  response: SearchResponse | null
  loading: boolean
  error: string | null
  onQueryChange: (query: string) => void
  onFiltersChange: (filters: SearchFilters) => void
}

type FilterChoices = {
  kind: '' | 'FILE' | 'DIRECTORY' | 'SYMBOLIC_LINK'
  extension: string
  modifiedDays: '' | '1' | '7' | '30' | '365'
  sizeRange: '' | 'UNDER_1_MB' | 'ONE_TO_TEN_MB' | 'TEN_TO_HUNDRED_MB' | 'OVER_100_MB'
}

const EMPTY_FILTERS: FilterChoices = { kind: '', extension: '', modifiedDays: '', sizeRange: '' }
const MEBIBYTE = 1024 ** 2

export function SearchPanel({ query, response, loading, error, onQueryChange, onFiltersChange }: SearchPanelProps) {
  const hasQuery = query.trim().length > 0
  const [filters, setFilters] = useState<FilterChoices>(EMPTY_FILTERS)

  function applyFilters(next: FilterChoices) {
    setFilters(next)
    onFiltersChange(toApiFilters(next))
  }

  return (
    <section className="search-panel" aria-labelledby="search-heading">
      <div className="search-heading-row">
        <div>
          <p className="section-kicker">Local search</p>
          <h2 id="search-heading">Find your files</h2>
        </div>
        {response ? (
          <p className="result-timing">
            {response.totalHits.toLocaleString()} {response.totalHits === 1 ? 'result' : 'results'} · {response.tookMs} ms
          </p>
        ) : null}
      </div>

      <fieldset className="search-filters">
        <legend>Filter results</legend>
        <label>
          Entry type
          <select value={filters.kind} onChange={(event) => applyFilters({ ...filters, kind: event.target.value as FilterChoices['kind'] })}>
            <option value="">All entries</option>
            <option value="FILE">Files</option>
            <option value="DIRECTORY">Folders</option>
            <option value="SYMBOLIC_LINK">Links</option>
          </select>
        </label>
        <label>
          Extension
          <input
            value={filters.extension}
            onChange={(event) => applyFilters({ ...filters, extension: event.target.value })}
            placeholder="pdf"
            maxLength={32}
            aria-label="File extension"
          />
        </label>
        <label>
          Modified
          <select value={filters.modifiedDays} onChange={(event) => applyFilters({ ...filters, modifiedDays: event.target.value as FilterChoices['modifiedDays'] })}>
            <option value="">Any time</option>
            <option value="1">Last 24 hours</option>
            <option value="7">Last 7 days</option>
            <option value="30">Last 30 days</option>
            <option value="365">Last year</option>
          </select>
        </label>
        <label>
          Size
          <select value={filters.sizeRange} onChange={(event) => applyFilters({ ...filters, sizeRange: event.target.value as FilterChoices['sizeRange'] })}>
            <option value="">Any size</option>
            <option value="UNDER_1_MB">Under 1 MB</option>
            <option value="ONE_TO_TEN_MB">1–10 MB</option>
            <option value="TEN_TO_HUNDRED_MB">10–100 MB</option>
            <option value="OVER_100_MB">Over 100 MB</option>
          </select>
        </label>
        <button type="button" className="clear-filters" disabled={sameFilters(filters, EMPTY_FILTERS)} onClick={() => applyFilters(EMPTY_FILTERS)}>
          Clear filters
        </button>
      </fieldset>

      <label className="visually-hidden" htmlFor="search">Search filenames, paths, and document content</label>
      <div className="search-box">
        <span aria-hidden="true">⌕</span>
        <input
          id="search"
          type="search"
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder="Try invoice, refund policy, or a folder name"
          autoComplete="off"
        />
        {loading ? <span className="searching-label" role="status">Searching…</span> : null}
      </div>

      {error ? <p className="message error-message" role="alert">{error}</p> : null}

      {!hasQuery ? (
        <div className="search-empty">
          <p>Search by a filename, folder, or words inside a supported document.</p>
          <span>Content search supports text, Markdown, common source files, PDF, and DOCX.</span>
        </div>
      ) : null}

      {hasQuery && !loading && response?.results.length === 0 ? (
        <div className="search-empty" role="status">
          <p>No results for “{response.query}”.</p>
          <span>Check the spelling, try fewer words, or confirm the folder has been indexed.</span>
        </div>
      ) : null}

      {response && response.results.length > 0 ? (
        <ol className="results-list" aria-label="Search results">
          {response.results.map((result) => <ResultCard key={result.path} result={result} />)}
        </ol>
      ) : null}
    </section>
  )
}

function ResultCard({ result }: { result: SearchResult }) {
  const [pendingAction, setPendingAction] = useState<'open' | 'reveal' | 'copy' | null>(null)
  const [feedback, setFeedback] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  async function performSystemAction(action: 'open' | 'reveal') {
    setPendingAction(action)
    setFeedback(null)
    setActionError(null)
    try {
      if (action === 'open') await openFile(result.path)
      else await revealFile(result.path)
      setFeedback(action === 'open' ? 'Opened with the default application.' : 'Shown in the system file manager.')
    } catch (error) {
      setActionError(error instanceof DeepFindApiError ? error.message : 'DeepFind could not complete the action.')
    } finally {
      setPendingAction(null)
    }
  }

  async function copyPath(value: string, label: string) {
    setPendingAction('copy')
    setFeedback(null)
    setActionError(null)
    try {
      if (!navigator.clipboard) throw new Error('Clipboard unavailable')
      await navigator.clipboard.writeText(value)
      setFeedback(`${label} copied.`)
    } catch {
      setActionError('DeepFind could not access the clipboard.')
    } finally {
      setPendingAction(null)
    }
  }

  return (
    <li className="result-card">
      <div className="file-icon" aria-hidden="true">{result.type === 'DIRECTORY' ? 'D' : result.extension.slice(0, 3) || 'FILE'}</div>
      <div className="result-copy">
        <div className="result-title-row">
          <h3>{result.filename}</h3>
          <span className="match-badge">{matchLabel(result.matchType)}</span>
        </div>
        <p className="result-path" title={result.path}>{result.path}</p>
        {result.snippet ? <p className="result-snippet">{renderSnippet(result.snippet)}</p> : null}
        <p className="result-meta">{typeLabel(result)} · Modified {formatDate(result.modifiedAt)}</p>
        <div className="result-actions" aria-label={`Actions for ${result.filename}`}>
          <button type="button" onClick={() => void performSystemAction('open')} disabled={pendingAction !== null}>
            {pendingAction === 'open' ? 'Opening…' : 'Open'}
          </button>
          <button type="button" onClick={() => void performSystemAction('reveal')} disabled={pendingAction !== null}>
            {pendingAction === 'reveal' ? 'Showing…' : 'Show in Folder'}
          </button>
          <button type="button" onClick={() => void copyPath(result.path, 'Full path')} disabled={pendingAction !== null}>
            Copy Path
          </button>
          <button
            type="button"
            onClick={() => void copyPath(containingFolder(result.path), 'Folder path')}
            disabled={pendingAction !== null}
          >
            Copy Folder
          </button>
        </div>
        <div className="action-feedback" aria-live="polite">
          {feedback ? <p>{feedback}</p> : null}
          {actionError ? <p className="action-error" role="alert">{actionError}</p> : null}
        </div>
      </div>
    </li>
  )
}

function containingFolder(path: string) {
  const separatorIndex = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'))
  if (separatorIndex < 0) return path
  if (separatorIndex === 0) return path.slice(0, 1)
  if (separatorIndex === 2 && /^[a-z]:/i.test(path)) return path.slice(0, 3)
  return path.slice(0, separatorIndex)
}

function matchLabel(matchType: SearchResult['matchType']) {
  return {
    EXACT_FILENAME: 'Exact filename',
    FILENAME_PREFIX: 'Filename prefix',
    FILENAME: 'Filename',
    PATH: 'Folder path',
    EXACT_PHRASE: 'Exact phrase',
    CONTENT: 'Document content',
  }[matchType]
}

function toApiFilters(filters: FilterChoices): SearchFilters {
  const result: SearchFilters = {}
  if (filters.kind) result.kind = filters.kind
  if (filters.extension.trim()) result.extension = filters.extension.trim()
  if (filters.modifiedDays) {
    result.modifiedAfter = new Date(Date.now() - Number(filters.modifiedDays) * 24 * 60 * 60 * 1_000).toISOString()
  }
  if (filters.sizeRange === 'UNDER_1_MB') result.maxSizeBytes = MEBIBYTE - 1
  if (filters.sizeRange === 'ONE_TO_TEN_MB') {
    result.minSizeBytes = MEBIBYTE
    result.maxSizeBytes = 10 * MEBIBYTE - 1
  }
  if (filters.sizeRange === 'TEN_TO_HUNDRED_MB') {
    result.minSizeBytes = 10 * MEBIBYTE
    result.maxSizeBytes = 100 * MEBIBYTE - 1
  }
  if (filters.sizeRange === 'OVER_100_MB') result.minSizeBytes = 100 * MEBIBYTE
  return result
}

function sameFilters(first: FilterChoices, second: FilterChoices) {
  return Object.keys(first).every((key) => first[key as keyof FilterChoices] === second[key as keyof FilterChoices])
}

function renderSnippet(snippet: SearchSnippet) {
  const parts: ReactNode[] = []
  let cursor = 0
  snippet.highlights.forEach((highlight, index) => {
    if (highlight.start < cursor || highlight.end <= highlight.start || highlight.end > snippet.text.length) return
    if (highlight.start > cursor) {
      parts.push(<span key={`text-${index}`}>{snippet.text.slice(cursor, highlight.start)}</span>)
    }
    parts.push(<mark key={`highlight-${index}`}>{snippet.text.slice(highlight.start, highlight.end)}</mark>)
    cursor = highlight.end
  })
  if (cursor < snippet.text.length) parts.push(<span key="text-final">{snippet.text.slice(cursor)}</span>)
  return parts
}

function typeLabel(result: SearchResult) {
  if (result.type === 'DIRECTORY') return 'Folder'
  if (result.type === 'SYMBOLIC_LINK') return 'Link'
  return `${result.extension ? result.extension.toUpperCase() + ' · ' : ''}${formatSize(result.sizeBytes)}`
}

function formatSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value))
}
