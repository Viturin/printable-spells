import { useEffect, useState } from "react";

type SpellSummary = {
  id: string;
  name: string;
  level: number;
  kind: "SPELL" | "FOCUS" | "RITUAL";
  rarity: string;
  traditions: string[];
  traits: string[];
  actionCost?: string;
  range?: string;
  target?: string;
  description?: string;
  score: number;
};

type SpellSearchResponse = {
  items: SpellSummary[];
  nextCursor?: string | null;
};

type ApiErrorResponse = {
  code?: string;
  message?: string;
  details?: string[];
};

export function App() {
  const [query, setQuery] = useState("daze");
  const [items, setItems] = useState<SpellSummary[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    const timeoutId = window.setTimeout(async () => {
      await runSearch(controller.signal);
    }, 250);

    return () => {
      controller.abort();
      window.clearTimeout(timeoutId);
    };
  }, [query]);

  async function runSearch(signal: AbortSignal) {
    const trimmed = query.trim();
    if (!trimmed) {
      setItems([]);
      setError(null);
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams({ q: trimmed, limit: "20" });
      const response = await fetch(`/api/v1/spells/search?${params.toString()}`, { signal });
      if (!response.ok) {
        setItems([]);
        setError(await buildErrorMessage(response));
        return;
      }

      const data = (await response.json()) as SpellSearchResponse;
      setItems(data.items ?? []);
    } catch (requestError) {
      if (requestError instanceof DOMException && requestError.name === "AbortError") {
        return;
      }
      const message =
        requestError instanceof Error ? requestError.message : "Search request failed.";
      setItems([]);
      setError(message);
    } finally {
      setIsLoading(false);
    }
  }

  async function buildErrorMessage(response: Response): Promise<string> {
    const fallback = mapStatusToMessage(response.status);
    const contentType = response.headers.get("content-type") ?? "";
    if (!contentType.includes("application/json")) {
      return fallback;
    }

    try {
      const errorBody = (await response.json()) as ApiErrorResponse;
      const message = errorBody.message?.trim();
      const details = (errorBody.details ?? []).filter((value) => value.trim().length > 0);

      if (message && details.length > 0) {
        return `${message} (${details.join("; ")})`;
      }
      if (message) {
        return message;
      }
      return fallback;
    } catch {
      return fallback;
    }
  }

  function mapStatusToMessage(status: number): string {
    if (status === 400) {
      return "Invalid search query.";
    }
    if (status === 404) {
      return "Search endpoint not found.";
    }
    if (status >= 500) {
      return "Server error while searching spells.";
    }
    return `Search failed (${status}).`;
  }

  return (
    <main className="page">
      <section className="card cardFixed">
        <h1>Printable Spells</h1>
        <p>Search spells from the backend REST API.</p>

        <form className="searchForm">
          <label htmlFor="spell-query">Search Query</label>
          <input
            id="spell-query"
            type="text"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search spells"
          />

          <button type="button" disabled>
            {isLoading ? "Searching..." : "Type to search"}
          </button>
        </form>

        {error ? <p className="error">{error}</p> : null}

        <ul className="results">
          {items.map((spell) => (
            <li key={spell.id} className="resultItem">
              <div className="resultHeader">
                <strong>{spell.name}</strong>
                <span>
                  L{spell.level} • {spell.kind.toLowerCase()} • {spell.rarity}
                </span>
              </div>
              <div className="resultMeta">
                {spell.traditions.length > 0 ? spell.traditions.join(", ") : "no traditions"}
              </div>
              {spell.description ? <p className="resultDescription">{spell.description}</p> : null}
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
