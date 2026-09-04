import { useState } from 'react'
import {
  DeepFindApiError,
  openFile,
  revealFile,
  type SearchResponse,
  type SearchResult,
} from '../api/deepfindApi'

interface SearchPanelProps {
  query: string
  response: SearchResponse | null
  loading: boolean
  error: string | null
  onQueryChange: (query: string) => void
}

export function SearchPanel({ query, response, loading, error, onQueryChange }: SearchPanelProps) {
  const hasQuery = query.trim().length > 0

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

      <label className="visually-hidden" htmlFor="search">Search filenames and paths</label>
      <div className="search-box">
        <span aria-hidden="true">⌕</span>
        <input
          id="search"
          type="search"
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder="Try invoice, thesis final, or a folder name"
          autoComplete="off"
        />
        {loading ? <span className="searching-label" role="status">Searching…</span> : null}
      </div>

      {error ? <p className="message error-message" role="alert">{error}</p> : null}

      {!hasQuery ? (
        <div className="search-empty">
          <p>Search by the filename or any folder you remember.</p>
          <span>Document content search arrives in Phase 2.</span>
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
  }[matchType]
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
