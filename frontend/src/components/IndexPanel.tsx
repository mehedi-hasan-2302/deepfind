import type { FormEvent } from 'react'
import type { IndexStatus } from '../api/deepfindApi'

interface IndexPanelProps {
  root: string
  status: IndexStatus | null
  error: string | null
  onRootChange: (root: string) => void
  onStart: () => Promise<void>
}

export function IndexPanel({ root, status, error, onRootChange, onStart }: IndexPanelProps) {
  const isRunning = status?.state === 'RUNNING'

  function submit(event: FormEvent) {
    event.preventDefault()
    void onStart()
  }

  return (
    <aside className="index-panel" aria-labelledby="index-heading">
      <div className="panel-heading">
        <div>
          <p className="section-kicker">Index location</p>
          <h2 id="index-heading">Where should DeepFind look?</h2>
        </div>
        <span className={`state-pill state-${status?.state.toLowerCase() ?? 'connecting'}`}>
          {statusLabel(status)}
        </span>
      </div>

      <form onSubmit={submit} className="root-form">
        <label htmlFor="index-root">Folder path</label>
        <div className="root-controls">
          <input
            id="index-root"
            value={root}
            onChange={(event) => onRootChange(event.target.value)}
            placeholder="C:\Users\You\Documents"
            autoComplete="off"
            disabled={isRunning}
          />
          <button type="submit" disabled={isRunning || root.trim().length === 0}>
            {isRunning ? 'Indexing…' : 'Start indexing'}
          </button>
        </div>
        <p className="field-help">Enter a full folder path. A native folder picker will arrive with desktop packaging.</p>
      </form>

      {error ? <p className="message error-message" role="alert">{error}</p> : null}

      <div className="index-status" aria-live="polite">
        {isRunning ? <progress aria-label="Indexing files" /> : null}
        <p className="status-summary">{statusSummary(status)}</p>
        {status?.currentPath ? <p className="current-path" title={status.currentPath}>{status.currentPath}</p> : null}
        {status && status.state !== 'IDLE' ? (
          <dl className="metrics">
            <Metric label="Discovered" value={status.entriesDiscovered} />
            <Metric label="Indexed" value={status.entriesIndexed} />
            <Metric label="Skipped" value={status.entriesSkipped} />
            <Metric label="Errors" value={status.failures} tone={status.failures > 0 ? 'warning' : undefined} />
          </dl>
        ) : null}
        {status?.lastFailure ? (
          <p className="message warning-message">
            {status.lastFailure.message} Other readable files continue to be indexed.
          </p>
        ) : null}
      </div>
    </aside>
  )
}

function Metric({ label, value, tone }: { label: string; value: number; tone?: string }) {
  return (
    <div className={tone ? `metric metric-${tone}` : 'metric'}>
      <dt>{label}</dt>
      <dd>{value.toLocaleString()}</dd>
    </div>
  )
}

function statusLabel(status: IndexStatus | null) {
  if (!status) return 'Connecting'
  return {
    IDLE: 'Not started',
    RUNNING: 'Indexing',
    COMPLETED: 'Ready',
    FAILED: 'Needs attention',
  }[status.state]
}

function statusSummary(status: IndexStatus | null) {
  if (!status) return 'Connecting to the local search service…'
  if (status.state === 'IDLE') return 'Choose a folder to make its filenames and paths searchable.'
  if (status.state === 'RUNNING') return 'Indexing is in progress. Search results may still be incomplete.'
  if (status.state === 'FAILED') return status.errorMessage ?? 'Indexing stopped before it could finish.'
  return `${status.entriesIndexed.toLocaleString()} entries are ready to search.`
}
