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
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: undefined })
  })

  it('explains the privacy promise and guides initial indexing', async () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: /find the file you remember/i })).toBeInTheDocument()
    expect(screen.getByText(/your files stay on your computer/i)).toBeInTheDocument()
    expect(await screen.findByText(/choose a folder to make its filenames/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /start indexing/i })).toBeDisabled()
  })

  it('restores the last selected folder from local backend state', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ ...idleStatus, root: 'D:\\資料\\DeepFind' })))

    render(<App />)

    expect(await screen.findByDisplayValue('D:\\資料\\DeepFind')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /start indexing/i })).toBeEnabled()
  })

  it('explains that an interrupted scan can be restarted', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({
      ...idleStatus,
      jobId: 'interrupted-job',
      state: 'INTERRUPTED',
      root: 'D:\\Archive',
      entriesDiscovered: 250,
      entriesIndexed: 225,
      errorMessage: 'The previous indexing run was interrupted. Start indexing again to reconcile this folder.',
      startedAt: '2026-09-05T01:00:00Z',
      finishedAt: '2026-09-05T01:02:00Z',
    })))

    render(<App />)

    expect(await screen.findByText('Interrupted')).toBeInTheDocument()
    expect(screen.getByText(/start indexing again to reconcile/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /start indexing/i })).toBeEnabled()
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

  it('shows live freshness and manually refreshes the selected folder', async () => {
    const readyStatus: IndexStatus = {
      ...idleStatus,
      jobId: 'completed-job',
      state: 'COMPLETED',
      root: 'C:\\Docs',
      entriesDiscovered: 42,
      entriesIndexed: 42,
      finishedAt: '2026-09-04T08:00:02Z',
    }
    const refreshingStatus: IndexStatus = {
      ...readyStatus,
      jobId: 'refresh-job',
      state: 'RUNNING',
      currentPath: 'C:\\Docs',
      entriesDiscovered: 0,
      entriesIndexed: 0,
      finishedAt: null,
    }
    let statusRequests = 0
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = input.toString()
      if (url === '/api/index/status') {
        statusRequests += 1
        return jsonResponse(readyStatus)
      }
      if (url === '/api/index/watch-status') {
        return jsonResponse({ root: 'C:\\Docs', state: 'WATCHING', message: 'Filesystem changes are being tracked.' })
      }
      if (url === '/api/index/refresh' && init?.method === 'POST') return jsonResponse(refreshingStatus, 202)
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    expect(await screen.findByText('Live updates active')).toBeInTheDocument()
    const refreshButton = screen.getByRole('button', { name: /refresh index/i })
    expect(refreshButton).toBeEnabled()
    fireEvent.click(refreshButton)

    expect(await screen.findByText(/live updates paused/i)).toBeInTheDocument()
    expect(await screen.findByText(/42 entries are ready to search/i, {}, { timeout: 2_000 })).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/index/refresh', { method: 'POST' })
    expect(statusRequests).toBeGreaterThan(1)
  })

  it('debounces content search and renders useful result context', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = input.toString()
      if (url === '/api/index/status') return jsonResponse(idleStatus)
      if (url.startsWith('/api/search?')) {
        return jsonResponse({
          query: 'salary expectation',
          tookMs: 4,
          totalHits: 1,
          results: [{
            path: 'D:\\Archive\\Thesis\\final_submission.docx',
            filename: 'final_submission.docx',
            extension: 'docx',
            type: 'FILE',
            sizeBytes: 2048,
            modifiedAt: '2026-08-31T10:30:00Z',
            matchType: 'CONTENT',
            snippet: {
              text: 'Request a refund. <img src=x onerror=alert(1)>',
              highlights: [{ start: 10, end: 16 }],
            },
          }],
        })
      }
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'salary expectation' } })

    expect(await screen.findByRole('heading', { name: 'final_submission.docx' })).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: /search results/i })).getByText('Document content')).toBeInTheDocument()
    expect(screen.getByText('refund').tagName).toBe('MARK')
    expect(screen.getByText(/<img src=x onerror=alert\(1\)>/)).toBeInTheDocument()
    expect(document.querySelector('.result-snippet img')).toBeNull()
    expect(screen.getByText(/2\.0 KB/)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/search?query=salary+expectation&limit=50',
      expect.objectContaining({ signal: expect.anything() }),
    )
  })

  it('explains an exact quoted-phrase result', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = input.toString()
      if (url === '/api/index/status') return jsonResponse(idleStatus)
      if (url.startsWith('/api/search?')) {
        return jsonResponse({
          query: '"annual budget report"',
          tookMs: 3,
          totalHits: 1,
          results: [{
            path: 'D:\\Archive\\board-minutes.txt',
            filename: 'board-minutes.txt',
            extension: 'txt',
            type: 'FILE',
            sizeBytes: 512,
            modifiedAt: '2026-09-01T10:30:00Z',
            matchType: 'EXACT_PHRASE',
            snippet: {
              text: 'The annual budget report was approved.',
              highlights: [{ start: 4, end: 10 }, { start: 11, end: 17 }, { start: 18, end: 24 }],
            },
          }],
        })
      }
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: '"annual budget report"' } })

    expect(await screen.findByText('Exact phrase')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'board-minutes.txt' })).toBeInTheDocument()
  })

  it('explains a fuzzy filename fallback result', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = input.toString()
      if (url === '/api/index/status') return jsonResponse(idleStatus)
      if (url.startsWith('/api/search?')) {
        return jsonResponse({
          query: 'reciept',
          tookMs: 2,
          totalHits: 1,
          results: [{
            path: 'D:\\Archive\\receipt.pdf',
            filename: 'receipt.pdf',
            extension: 'pdf',
            type: 'FILE',
            sizeBytes: 512,
            modifiedAt: '2026-09-01T10:30:00Z',
            matchType: 'FUZZY_FILENAME',
            snippet: null,
          }],
        })
      }
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'reciept' } })

    expect(await screen.findByText('Similar filename')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'receipt.pdf' })).toBeInTheDocument()
  })

  it('sends explicit type, extension, and size filters without changing the query text', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = input.toString()
      if (url === '/api/index/status') return jsonResponse(idleStatus)
      if (url.startsWith('/api/search?')) {
        return jsonResponse({ query: 'invoice', tookMs: 1, totalHits: 0, results: [] })
      }
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'invoice' } })
    fireEvent.change(screen.getByLabelText('Entry type'), { target: { value: 'FILE' } })
    fireEvent.change(screen.getByLabelText('File extension'), { target: { value: 'pdf' } })
    fireEvent.change(screen.getByLabelText('Size'), { target: { value: 'ONE_TO_TEN_MB' } })

    expect(await screen.findByText('No results for “invoice”.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/search?query=invoice&limit=50&kind=FILE&extension=pdf&minSizeBytes=1048576&maxSizeBytes=10485759',
      expect.objectContaining({ signal: expect.anything() }),
    )

    fireEvent.click(screen.getByRole('button', { name: /clear filters/i }))
    expect(screen.getByLabelText('Entry type')).toHaveValue('')
    expect(screen.getByLabelText('File extension')).toHaveValue('')
  })

  it('opens, reveals, and copies paths from a result card', async () => {
    const resultPath = 'D:\\Archive\\Thesis\\final_submission.docx'
    const writeText = vi.fn(async () => undefined)
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = input.toString()
      if (url === '/api/index/status') return jsonResponse(idleStatus)
      if (url.startsWith('/api/search?')) {
        return jsonResponse({
          query: 'final',
          tookMs: 2,
          totalHits: 1,
          results: [{
            path: resultPath,
            filename: 'final_submission.docx',
            extension: 'docx',
            type: 'FILE',
            sizeBytes: 2048,
            modifiedAt: '2026-08-31T10:30:00Z',
            matchType: 'FILENAME_PREFIX',
          }],
        })
      }
      if (url === '/api/files/open') return jsonResponse({ action: 'OPENED' }, 202)
      if (url === '/api/files/reveal') return jsonResponse({ action: 'REVEALED' }, 202)
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'final' } })
    const resultHeading = await screen.findByRole('heading', { name: 'final_submission.docx' })
    const resultCard = within(resultHeading.closest('li')!)

    fireEvent.click(resultCard.getByRole('button', { name: 'Open' }))
    expect(await resultCard.findByText(/opened with the default application/i)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/files/open', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ path: resultPath }),
    }))

    fireEvent.click(resultCard.getByRole('button', { name: /show in folder/i }))
    expect(await resultCard.findByText(/shown in the system file manager/i)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/files/reveal', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ path: resultPath }),
    }))

    fireEvent.click(resultCard.getByRole('button', { name: 'Copy Path' }))
    expect(await resultCard.findByText('Full path copied.')).toBeInTheDocument()
    expect(writeText).toHaveBeenLastCalledWith(resultPath)

    fireEvent.click(resultCard.getByRole('button', { name: 'Copy Folder' }))
    expect(await resultCard.findByText('Folder path copied.')).toBeInTheDocument()
    expect(writeText).toHaveBeenLastCalledWith('D:\\Archive\\Thesis')
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
