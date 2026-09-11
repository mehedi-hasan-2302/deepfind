import { useEffect, useRef, useState } from 'react'
import {
  DeepFindApiError,
  getIndexWatchStatus,
  getIndexExclusions,
  getIndexStatus,
  pauseIndex,
  refreshIndex,
  resumeIndex,
  searchFiles,
  startIndex,
  updateIndexExclusions,
  type IndexStatus,
  type IndexWatchStatus,
  type SearchResponse,
  type SearchFilters,
} from './api/deepfindApi'
import { IndexPanel } from './components/IndexPanel'
import { SearchPanel } from './components/SearchPanel'

const SEARCH_DELAY_MS = 300
const STATUS_POLL_MS = 500
const WATCH_STATUS_POLL_MS = 2_000

function App() {
  const [root, setRoot] = useState('')
  const [indexStatus, setIndexStatus] = useState<IndexStatus | null>(null)
  const [watchStatus, setWatchStatus] = useState<IndexWatchStatus | null>(null)
  const [indexError, setIndexError] = useState<string | null>(null)
  const [exclusionText, setExclusionText] = useState('')
  const [query, setQuery] = useState('')
  const [searchResponse, setSearchResponse] = useState<SearchResponse | null>(null)
  const [searchLoading, setSearchLoading] = useState(false)
  const [paginationLoading, setPaginationLoading] = useState(false)
  const [searchError, setSearchError] = useState<string | null>(null)
  const [searchFilters, setSearchFilters] = useState<SearchFilters>({})
  const paginationController = useRef<AbortController | null>(null)

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
    const selectedRoot = indexStatus?.root
    if (!selectedRoot) {
      setExclusionText('')
      return
    }
    const controller = new AbortController()
    void getIndexExclusions(controller.signal)
      .then((settings) => {
        if (settings.root === selectedRoot) setExclusionText((settings.paths ?? []).join('\n'))
      })
      .catch((error: unknown) => {
        if (!isAbort(error)) setIndexError(errorMessage(error))
      })
    return () => controller.abort()
  }, [indexStatus?.root])

  useEffect(() => {
    let active = true
    let timer = 0

    async function pollWatchStatus() {
      try {
        const status = await getIndexWatchStatus()
        if (active) setWatchStatus(status)
      } catch {
        if (active) setWatchStatus(null)
      } finally {
        if (active) timer = window.setTimeout(pollWatchStatus, WATCH_STATUS_POLL_MS)
      }
    }

    void pollWatchStatus()
    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [])

  useEffect(() => {
    if (indexStatus?.state !== 'RUNNING' && indexStatus?.state !== 'PAUSING') return
    let active = true
    let timer = 0

    async function poll() {
      try {
        const status = await getIndexStatus()
        if (!active) return
        setIndexStatus(status)
        setIndexError(null)
        if (status.state === 'RUNNING' || status.state === 'PAUSING') timer = window.setTimeout(poll, STATUS_POLL_MS)
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
      void searchFiles(normalizedQuery, 50, 0, searchFilters, controller.signal)
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
  }, [query, searchFilters])

  function changeQuery(nextQuery: string) {
    cancelPagination()
    setQuery(nextQuery)
    setSearchResponse(null)
    setSearchError(null)
    if (!nextQuery.trim()) setSearchLoading(false)
  }

  function changeFilters(nextFilters: SearchFilters) {
    cancelPagination()
    setSearchFilters(nextFilters)
  }

  function cancelPagination() {
    paginationController.current?.abort()
    paginationController.current = null
    setPaginationLoading(false)
  }

  async function loadMoreResults() {
    const normalizedQuery = query.trim()
    if (!normalizedQuery || !searchResponse?.hasMore || searchLoading || paginationLoading) return

    const expectedOffset = searchResponse.results.length
    const controller = new AbortController()
    paginationController.current?.abort()
    paginationController.current = controller
    setPaginationLoading(true)
    setSearchError(null)
    try {
      const nextPage = await searchFiles(normalizedQuery, 50, expectedOffset, searchFilters, controller.signal)
      if (controller.signal.aborted) return
      setSearchResponse((current) => {
        if (!current || current.query !== nextPage.query || current.results.length !== expectedOffset) return current
        return {
          ...nextPage,
          offset: 0,
          limit: current.results.length + nextPage.results.length,
          results: [...current.results, ...nextPage.results],
        }
      })
    } catch (error) {
      if (!isAbort(error)) setSearchError(errorMessage(error))
    } finally {
      if (paginationController.current === controller) {
        paginationController.current = null
        setPaginationLoading(false)
      }
    }
  }

  async function beginIndexing() {
    setIndexError(null)
    try {
      setIndexStatus(await startIndex(root.trim()))
    } catch (error) {
      setIndexError(errorMessage(error))
    }
  }

  async function refreshSelectedRoot() {
    setIndexError(null)
    try {
      setIndexStatus(await refreshIndex())
      setWatchStatus(await getIndexWatchStatus())
    } catch (error) {
      setIndexError(errorMessage(error))
    }
  }

  async function pauseIndexing() {
    setIndexError(null)
    try {
      setIndexStatus(await pauseIndex())
    } catch (error) {
      setIndexError(errorMessage(error))
    }
  }

  async function resumeIndexing() {
    setIndexError(null)
    try {
      setIndexStatus(await resumeIndex())
    } catch (error) {
      setIndexError(errorMessage(error))
    }
  }

  async function saveExclusions() {
    setIndexError(null)
    try {
      const paths = exclusionText.split(/\r?\n/).map((path) => path.trim()).filter(Boolean)
      const response = await updateIndexExclusions(paths)
      setExclusionText(response.paths.join('\n'))
      setIndexStatus(response.reconciliation)
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
        <p className="intro">Search filenames, folders, and supported document text from a private index on this device.</p>
      </section>

      <div className="workspace">
        <IndexPanel
          root={root}
          status={indexStatus}
          watchStatus={watchStatus}
          error={indexError}
          onRootChange={setRoot}
          onStart={beginIndexing}
          onRefresh={refreshSelectedRoot}
          onPause={pauseIndexing}
          onResume={resumeIndexing}
          exclusionText={exclusionText}
          onExclusionTextChange={setExclusionText}
          onSaveExclusions={saveExclusions}
        />
        <SearchPanel
          query={query}
          response={searchResponse}
          loading={searchLoading}
          loadingMore={paginationLoading}
          error={searchError}
          onQueryChange={changeQuery}
          onFiltersChange={changeFilters}
          onLoadMore={loadMoreResults}
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
