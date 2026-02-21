import { type KeyboardEvent, useEffect, useState } from "react";

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

type SpellListProps = {
  spells: SpellSummary[];
  onSpellClick: (spell: SpellSummary) => void;
  onSpellKeyDown: (event: KeyboardEvent<HTMLElement>, action: () => void) => void;
  ariaLabelPrefix: string;
};

function SpellList({ spells, onSpellClick, onSpellKeyDown, ariaLabelPrefix }: SpellListProps) {
  return (
    <ul className="results">
      {spells.map((spell) => (
        <li
          key={spell.id}
          className="resultItem resultItemClickable"
          role="button"
          tabIndex={0}
          onClick={() => onSpellClick(spell)}
          onKeyDown={(event) => onSpellKeyDown(event, () => onSpellClick(spell))}
          aria-label={`${ariaLabelPrefix} ${spell.name}`}
        >
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
  );
}

export function App() {
  const [query, setQuery] = useState("daze");
  const [items, setItems] = useState<SpellSummary[]>([]);
  const [selectedSpells, setSelectedSpells] = useState<SpellSummary[]>([]);
  const [, setIsLoading] = useState(false);
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

  function isSelected(spellId: string): boolean {
    return selectedSpells.some((spell) => spell.id === spellId);
  }

  function addSpell(spell: SpellSummary) {
    setSelectedSpells((current) => {
      if (current.some((item) => item.id === spell.id)) {
        return current;
      }
      return [...current, spell];
    });
  }

  function removeSpell(spellId: string) {
    setSelectedSpells((current) => current.filter((spell) => spell.id !== spellId));
  }

  function onSpellKeyDown(event: KeyboardEvent<HTMLElement>, action: () => void) {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      action();
    }
  }

  const searchableItems = items.filter((spell) => !isSelected(spell.id));

  return (
    <main className="page">
      <div className="columns">
        <section className="card cardFixed">
          <h2>Find Spells</h2>

          <form className="searchForm">
            <input
              id="spell-query"
              type="text"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search spells"
            />
          </form>

          {error ? <p className="error">{error}</p> : null}

          <SpellList
            spells={searchableItems}
            onSpellClick={addSpell}
            onSpellKeyDown={onSpellKeyDown}
            ariaLabelPrefix="Move to selected spells:"
          />
        </section>

        <section className="card cardFixed">
          <h2>Selected Spells</h2>
          <p>{selectedSpells.length} spell(s) selected.</p>

          <SpellList
            spells={selectedSpells}
            onSpellClick={(spell) => removeSpell(spell.id)}
            onSpellKeyDown={onSpellKeyDown}
            ariaLabelPrefix="Move back to search results:"
          />
        </section>
      </div>
    </main>
  );
}
