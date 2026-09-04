import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { IndexStatus } from './api/deepfindApi'

const idleStatus: IndexStatus = {
  jobId: null,
  state: 'IDLE',
  root: null,
  currentPath: null,
  entriesDiscovered: 0,
  filesDiscovered: 0,
  directoriesDiscovered: 0,
  symbolicLinksDiscovered: 0,
  otherEntriesDiscovered: 0,
  entriesSkipped: 0,
  failures: 0,
  entriesIndexed: 0,
  lastFailure: null,
  errorMessage: null,
  startedAt: null,
  finishedAt: null,
}

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(idleStatus)))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('explains the privacy promise and guides initial indexing', async () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: /find the file you remember/i })).toBeInTheDocument()
    expect(screen.getByText(/your files stay on your computer/i)).toBeInTheDocument()
    expect(await screen.findByText(/choose a folder to make its filenames/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /start indexing/i })).toBeDisabled()
  })

  it('starts indexing and polls until the index is ready', async () => {
    const runningStatus: IndexStatus = {
      ...idleStatus,
      jobId: 'job-1',
      state: 'RUNNING',
      root: 'C:\\Docs',
      currentPath: 'C:\\Docs\\drafts',
      entriesDiscovered: 12,
      entriesIndexed: 10,
      startedAt: '2026-09-04T08:00:00Z',
    }
    const completedStatus: IndexStatus = {
      ...runningStatus,
      state: 'COMPLETED',
      currentPath: null,
      entriesDiscovered: 42,
      entriesIndexed: 42,
      finishedAt: '2026-09-04T08:00:02Z',
    }
    let statusRequests = 0
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString()
      if (url === '/api/index/start' && init?.method === 'POST') return jsonResponse(runningStatus)
      if (url === '/api/index/status') {
        statusRequests += 1
        return jsonResponse(statusRequests === 1 ? idleStatus : completedStatus)
      }
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)
    fireEvent.change(screen.getByLabelText(/folder path/i), { target: { value: 'C:\\Docs' } })
    fireEvent.click(screen.getByRole('button', { name: /start indexing/i }))

    expect(await screen.findByText(/indexing is in progress/i)).toBeInTheDocument()
    expect(await screen.findByText(/42 entries are ready to search/i, {}, { timeout: 2_000 })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/index/start', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ root: 'C:\\Docs' }),
    }))
  })

  it('debounces metadata search and renders useful result context', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = input.toString()
      if (url === '/api/index/status') return jsonResponse(idleStatus)
      if (url.startsWith('/api/search?')) {
        return jsonResponse({
          query: 'thesis final',
          tookMs: 4,
          totalHits: 1,
          results: [{
            path: 'D:\\Archive\\Thesis\\final_submission.docx',
            filename: 'final_submission.docx',
            extension: 'docx',
            type: 'FILE',
            sizeBytes: 2048,
            modifiedAt: '2026-08-31T10:30:00Z',
            matchType: 'PATH',
          }],
        })
      }
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'thesis final' } })

    expect(await screen.findByRole('heading', { name: 'final_submission.docx' })).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: /search results/i })).getByText('Folder path')).toBeInTheDocument()
    expect(screen.getByText(/2\.0 KB/)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/search?query=thesis+final&limit=50',
      expect.objectContaining({ signal: expect.anything() }),
    )
  })

  it('shows backend and empty-result states without hiding the search interface', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = input.toString()
      if (url === '/api/index/status') throw new TypeError('connection refused')
      if (url.startsWith('/api/search?')) {
        return jsonResponse({ query: 'unknown', tookMs: 2, totalHits: 0, results: [] })
      }
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not reach its local search service/i)
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'unknown' } })
    expect(await screen.findByText('No results for “unknown”.')).toBeInTheDocument()
  })
})

function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}
