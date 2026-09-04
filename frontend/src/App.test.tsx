import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('explains the local-first privacy promise', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: /find the file you remember/i })).toBeInTheDocument()
    expect(screen.getByText(/your files stay on your computer/i)).toBeInTheDocument()
    expect(screen.getByRole('searchbox')).toBeDisabled()
  })
})
