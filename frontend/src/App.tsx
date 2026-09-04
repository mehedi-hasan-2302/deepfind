import { useEffect, useState } from 'react'
import {
  DeepFindApiError,
  getIndexStatus,
  searchFiles,
  startIndex,
  type IndexStatus,
  type SearchResponse,
} from './api/deepfindApi'
import { IndexPanel } from './components/IndexPanel'
import { SearchPanel } from './components/SearchPanel'

const SEARCH_DELAY_MS = 300
const STATUS_POLL_MS = 500

function App() {
  const [root, setRoot] = useState('')
  const [indexStatus, setIndexStatus] = useState<IndexStatus | null>(null)
  const [indexError, setIndexError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [searchResponse, setSearchResponse] = useState<SearchResponse | null>(null)
  const [searchLoading, setSearchLoading] = useState(false)
  const [searchError, setSearchError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    void getIndexStatus(controller.signal)
      .then((status) => {
        setIndexStatus(status)
        setIndexError(null)
        if (status.root) setRoot(status.root)
      })
      .catch((error: unknown) => {
        if (!isAbort(error)) setIndexError(errorMessage(error))
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (indexStatus?.state !== 'RUNNING') return
    let active = true
    let timer = 0

    async function poll() {
      try {
        const status = await getIndexStatus()
        if (!active) return
        setIndexStatus(status)
        setIndexError(null)
        if (status.state === 'RUNNING') timer = window.setTimeout(poll, STATUS_POLL_MS)
      } catch (error) {
        if (active) setIndexError(errorMessage(error))
      }
    }

    timer = window.setTimeout(poll, STATUS_POLL_MS)
    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [indexStatus?.state])

  useEffect(() => {
    const normalizedQuery = query.trim()
    if (!normalizedQuery) return
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setSearchLoading(true)
      setSearchError(null)
      void searchFiles(normalizedQuery, 50, controller.signal)
        .then(setSearchResponse)
        .catch((error: unknown) => {
          if (!isAbort(error)) setSearchError(errorMessage(error))
        })
        .finally(() => {
          if (!controller.signal.aborted) setSearchLoading(false)
        })
    }, SEARCH_DELAY_MS)

    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [query])

  function changeQuery(nextQuery: string) {
    setQuery(nextQuery)
    setSearchResponse(null)
    setSearchError(null)
    if (!nextQuery.trim()) setSearchLoading(false)
  }

  async function beginIndexing() {
    setIndexError(null)
    try {
      setIndexStatus(await startIndex(root.trim()))
    } catch (error) {
      setIndexError(errorMessage(error))
    }
  }

  return (
    <main className="app-shell">
      <header className="app-header">
        <a className="brand" href="#top" aria-label="DeepFind home">
          <span className="brand-mark" aria-hidden="true">D</span>
          <span>DeepFind</span>
        </a>
        <aside className="privacy-note" aria-label="Privacy promise">
          <strong>Your files stay on your computer.</strong>
          <span>No account, analytics, or document uploads.</span>
        </aside>
      </header>

      <section className="hero" id="top" aria-labelledby="welcome-heading">
        <p className="eyebrow">Private search for your computer</p>
        <h1 id="welcome-heading">Find the file you remember.</h1>
        <p className="intro">Search filenames and folders instantly from a private index stored on this device.</p>
      </section>

      <div className="workspace">
        <IndexPanel
          root={root}
          status={indexStatus}
          error={indexError}
          onRootChange={setRoot}
          onStart={beginIndexing}
        />
        <SearchPanel
          query={query}
          response={searchResponse}
          loading={searchLoading}
          error={searchError}
          onQueryChange={changeQuery}
        />
      </div>
    </main>
  )
}

function isAbort(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError'
}

function errorMessage(error: unknown) {
  return error instanceof DeepFindApiError ? error.message : 'DeepFind could not complete the request.'
}

export default App
