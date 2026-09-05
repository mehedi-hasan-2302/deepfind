export type IndexJobState = 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'INTERRUPTED'

export interface IndexFailure {
  path: string
  reason: string
  message: string
}

export interface IndexStatus {
  jobId: string | null
  state: IndexJobState
  root: string | null
  currentPath: string | null
  entriesDiscovered: number
  filesDiscovered: number
  directoriesDiscovered: number
  symbolicLinksDiscovered: number
  otherEntriesDiscovered: number
  entriesSkipped: number
  failures: number
  entriesIndexed: number
  lastFailure: IndexFailure | null
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
}

export type IndexWatchState = 'STOPPED' | 'WATCHING' | 'RECONCILIATION_REQUIRED' | 'FAILED'

export interface IndexWatchStatus {
  root: string | null
  state: IndexWatchState
  message: string
}

export interface SearchResult {
  path: string
  filename: string
  extension: string
  type: 'FILE' | 'DIRECTORY' | 'SYMBOLIC_LINK' | 'OTHER'
  sizeBytes: number
  modifiedAt: string
  matchType: 'EXACT_FILENAME' | 'FILENAME_PREFIX' | 'FILENAME' | 'PATH' | 'EXACT_PHRASE' | 'CONTENT'
  snippet: SearchSnippet | null
}

export interface SearchSnippet {
  text: string
  highlights: SearchHighlight[]
}

export interface SearchHighlight {
  start: number
  end: number
}

export interface SearchResponse {
  query: string
  tookMs: number
  totalHits: number
  results: SearchResult[]
}

export interface SearchFilters {
  kind?: 'FILE' | 'DIRECTORY' | 'SYMBOLIC_LINK' | 'OTHER'
  extension?: string
  modifiedAfter?: string
  modifiedBefore?: string
  minSizeBytes?: number
  maxSizeBytes?: number
}

export interface FileActionResponse {
  action: 'OPENED' | 'REVEALED'
}

interface ApiErrorBody {
  code?: string
  message?: string
}

export class DeepFindApiError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.name = 'DeepFindApiError'
    this.code = code
  }
}

export async function getIndexStatus(signal?: AbortSignal): Promise<IndexStatus> {
  return requestJson<IndexStatus>('/api/index/status', { signal })
}

export async function startIndex(root: string): Promise<IndexStatus> {
  return requestJson<IndexStatus>('/api/index/start', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ root }),
  })
}

export async function refreshIndex(): Promise<IndexStatus> {
  return requestJson<IndexStatus>('/api/index/refresh', { method: 'POST' })
}

export async function getIndexWatchStatus(signal?: AbortSignal): Promise<IndexWatchStatus> {
  return requestJson<IndexWatchStatus>('/api/index/watch-status', { signal })
}

export async function searchFiles(
  query: string,
  limit = 50,
  filters: SearchFilters = {},
  signal?: AbortSignal,
): Promise<SearchResponse> {
  const parameters = new URLSearchParams({ query, limit: String(limit) })
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== '') parameters.set(key, String(value))
  })
  return requestJson<SearchResponse>(`/api/search?${parameters}`, { signal })
}

export async function openFile(path: string): Promise<FileActionResponse> {
  return requestFileAction('/api/files/open', path)
}

export async function revealFile(path: string): Promise<FileActionResponse> {
  return requestFileAction('/api/files/reveal', path)
}

function requestFileAction(endpoint: string, path: string): Promise<FileActionResponse> {
  return requestJson<FileActionResponse>(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path }),
  })
}

async function requestJson<T>(input: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(input, init)
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    throw new DeepFindApiError('BACKEND_UNAVAILABLE', 'DeepFind could not reach its local search service.')
  }

  if (!response.ok) {
    let body: ApiErrorBody = {}
    try {
      body = (await response.json()) as ApiErrorBody
    } catch {
      // The stable fallback below handles non-JSON proxy and startup failures.
    }
    throw new DeepFindApiError(
      body.code ?? 'REQUEST_FAILED',
      body.message ?? 'DeepFind could not complete the request.',
    )
  }

  return (await response.json()) as T
}
