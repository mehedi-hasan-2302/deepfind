function App() {
  return (
    <main className="app-shell">
      <section className="welcome-card" aria-labelledby="welcome-heading">
        <p className="eyebrow">DeepFind</p>
        <h1 id="welcome-heading">Find the file you remember.</h1>
        <p className="intro">
          Search your computer by filename, location, and eventually the words inside your documents.
        </p>
        <label className="search-label" htmlFor="search">
          Search your computer
        </label>
        <input id="search" type="search" placeholder="Search is coming in Phase 1" disabled />
        <p className="status" role="status">
          Repository foundation ready. Indexing has not started.
        </p>
        <aside className="privacy-note" aria-label="Privacy promise">
          <strong>Your files stay on your computer.</strong>
          <span>No account, analytics, or document uploads.</span>
        </aside>
      </section>
    </main>
  )
}

export default App
