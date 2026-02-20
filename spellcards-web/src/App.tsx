import { useState } from "react";

export function App() {
  const [query, setQuery] = useState("daze");

  return (
    <main className="page">
      <section className="card">
        <h1>Printable Spells</h1>
        <p>Frontend module is ready. Next step: connect generated OpenAPI client.</p>

        <label htmlFor="spell-query">Search Query</label>
        <input
          id="spell-query"
          type="text"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search spells"
        />

        <button type="button" disabled>
          Search (next step)
        </button>
      </section>
    </main>
  );
}
