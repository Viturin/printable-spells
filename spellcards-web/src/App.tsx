import { lazy, Suspense, useEffect, useState } from "react";
import type { CreateSpellCardJobRequest, SpellCardPdfLayout, SpellSummary } from "./generated/client/src/models";
import { PathbuilderImport } from "./PathbuilderImport";
import { useSpellCardPdfJob } from "./useSpellCardPdfJob";

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
  onAdd: (spell: SpellSummary) => void;
};

function SpellDetails({ spell }: { spell: SpellSummary }) {
  const fields = [
    ["Type", spell.kind.toLowerCase()],
    ["Rarity", spell.rarity],
    ["Traditions", spell.traditions.join(", ")],
    ["Traits", spell.traits.join(", ")],
    ["Actions", spell.actionCost],
    ["Range", spell.range],
    ["Target", spell.target],
  ].filter(([, value]) => value);

  return (
    <div className="spellDetails">
      <dl className="spellFields">
        {fields.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      {spell.description ? <p className="spellDescription">{spell.description}</p> : null}
    </div>
  );
}

function SpellList({ spells, onAdd }: SpellListProps) {
  return (
    <ul className="spellList">
      {spells.map((spell) => (
        <li key={spell.id} className="spellRow">
          <details>
            <summary className="spellSummary">
              <span className="disclosureIcon" aria-hidden="true" />
              <span className="spellName">{spell.name}</span>
              <span className="spellKind">{spell.kind.toLowerCase()}</span>
              <span className="spellLevel" aria-label={`Level ${spell.level}`}>L{spell.level}</span>
            </summary>
            <SpellDetails spell={spell} />
          </details>
          <button
            className="spellAction"
            type="button"
            aria-label={`Add ${spell.name}`}
            onClick={() => onAdd(spell)}
          >
            Add
          </button>
        </li>
      ))}
    </ul>
  );
}

const PdfPreview = lazy(() => import("./PdfPreview"));

export function App() {
  const [query, setQuery] = useState("daze");
  const [items, setItems] = useState<SpellSummary[]>([]);
  const [selectedSpells, setSelectedSpells] = useState<SpellSummary[]>([]);
  const [pdfLayout, setPdfLayout] = useState<SpellCardPdfLayout>("A4_SHEET");
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [exportRequest, setExportRequest] = useState<CreateSpellCardJobRequest | null>(null);
  const spellNames = selectedSpells.map((spell) => spell.name);
  const preview = useSpellCardPdfJob({ spellNames, layout: "PLAYING_CARD" }, 650);
  const { job, retry, resumeChecking } = useSpellCardPdfJob(exportRequest);
  const exportActive = job.kind === "submitting" || job.kind === "checking";

  function generatePdf() {
    setExportRequest({ spellNames, layout: pdfLayout });
    retry();
  }

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
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams({ q: trimmed, limit: "20" });
      const response = await fetch(`/api/v1/spells/search?${params.toString()}`, { signal });
      if (!response.ok) {
        const message = await buildErrorMessage(response);
        if (!signal.aborted) {
          setItems([]);
          setError(message);
        }
        return;
      }

      const data = (await response.json()) as SpellSearchResponse;
      if (!signal.aborted) {
        setItems(data.items ?? []);
      }
    } catch (requestError) {
      if (signal.aborted) {
        return;
      }
      const message =
        requestError instanceof Error ? requestError.message : "Search request failed.";
      setItems([]);
      setError(message);
    } finally {
      if (!signal.aborted) {
        setIsLoading(false);
      }
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
    addSpells([spell]);
  }

  function addSpells(spells: SpellSummary[]) {
    setSelectedSpells((current) => {
      const byId = new Map(current.map((spell) => [spell.id, spell]));
      for (const spell of spells) {
        if (!byId.has(spell.id)) {
          byId.set(spell.id, spell);
        }
      }
      return [...byId.values()];
    });
  }

  function removeSpell(spellId: string) {
    setSelectedSpells((current) => current.filter((spell) => spell.id !== spellId));
  }

  const searchableItems = items.filter((spell) => !isSelected(spell.id));
  const previewUrl = preview.job.kind === "ready" ? preview.job.downloadUrl + "?inline=true" : undefined;
  const exportMatchesSelection = exportRequest?.layout === pdfLayout
    && JSON.stringify(exportRequest.spellNames) === JSON.stringify(spellNames);
  const hasSelection = selectedSpells.length > 0;

  return (
    <main className={hasSelection ? "page pageWithPdf" : "page"}>
      <header className="pageHeader">
        <span className="bookIcon" aria-hidden="true" />
        <div>
          <h1>Spell Cards</h1>
          <p>Choose spells and print them as a PDF.</p>
        </div>
      </header>
      <div className="columns">
        <section className="panel" aria-labelledby="search-heading">
          <div className="panelHeader">
            <h2 id="search-heading">Find spells</h2>
            <form className="searchForm" role="search" onSubmit={(event) => event.preventDefault()}>
              <span className="searchIcon" aria-hidden="true" />
              <input
                id="spell-query"
                type="search"
                aria-label="Search spells"
                value={query}
                onChange={(event) => {
                  setQuery(event.target.value);
                  setIsLoading(true);
                  setError(null);
                }}
                placeholder="Search by spell name"
              />
            </form>
            <PathbuilderImport onImport={addSpells} />
          </div>
          <div className="panelBody" aria-busy={isLoading}>
            {error ? <p className="emptyState error" role="alert">{error}</p> : null}
            {isLoading && searchableItems.length === 0 ? <p className="emptyState">Searching…</p> : null}
            {!error && !isLoading && searchableItems.length === 0 ? (
              <p className="emptyState">
                {!query.trim() ? "Enter a spell name to search." : items.length ? "All results are selected." : "No spells found."}
              </p>
            ) : null}
            {searchableItems.length ? <SpellList spells={searchableItems} onAdd={addSpell} /> : null}
          </div>
        </section>

        <section className="panel selectedPanel" aria-labelledby="selected-heading">
          <div className="panelHeader">
            <div className="selectedHeading">
              <h2 id="selected-heading">Selected spells</h2>
              <output className="selectedCount" aria-label="Selected spell count">
                {selectedSpells.length}
              </output>
            </div>
          </div>
          <div className="panelBody">
            {hasSelection ? (
              <>
                <ul className="selectedSpellList" aria-label="Spells in this PDF">
                  {selectedSpells.map((spell) => {
                    const dice = spell.cardPreview?.rules.find((rule) => rule.label === "Dice needed")
                      ?.value.match(/\bd\d+\b/g);
                    const duration = spell.cardPreview?.rules.find((rule) => rule.label === "Duration")?.value;
                    return (
                      <li key={spell.id}>
                        <span>{spell.name}{dice?.length ? (
                          <span className="visuallyHidden">. Dice needed: {dice.join(", ")}.</span>
                        ) : null}{duration ? (
                          <span className="visuallyHidden">. Duration: {duration}.</span>
                        ) : null}</span>
                        <button type="button" className="previewRemove" aria-label={"Remove " + spell.name}
                          onClick={() => removeSpell(spell.id)}>Remove</button>
                      </li>
                    );
                  })}
                </ul>
                {previewUrl ? (
                  <Suspense fallback={<p className="emptyState" role="status">Loading PDF preview…</p>}>
                    <PdfPreview key={previewUrl} url={previewUrl} />
                  </Suspense>
                ) : preview.job.kind === "failed" || preview.job.kind === "paused" ? (
                  <div className="emptyState">
                    <p className="error" role="status">{preview.job.message}</p>
                    <button type="button" onClick={preview.job.kind === "paused" ? preview.resumeChecking : preview.retry}>
                      Retry card preview
                    </button>
                  </div>
                ) : <p className="emptyState" role="status">Preparing card previews…</p>}
              </>
            ) : <p className="emptyState emptySelection">Add spells to preview your cards here.</p>}
          </div>
          <div className="panelFooter">
            <label className="pdfLayout">
              <span>PDF layout</span>
              <select
                value={pdfLayout}
                aria-describedby="pdf-layout-hint"
                onChange={(event) => setPdfLayout(event.target.value as SpellCardPdfLayout)}
              >
                <option value="A4_SHEET">A4 cut sheets</option>
                <option value="PLAYING_CARD">Playing-card pages</option>
              </select>
            </label>
            <p className="pdfLayoutHint" id="pdf-layout-hint">
              {pdfLayout === "A4_SHEET"
                ? "Up to 9 cards per sheet. Print at 100% to keep the card size."
                : "One 63.5 × 88.9 mm card per page."}
            </p>
            <div className="pdfActions">
              <button type="button" disabled={!hasSelection || exportActive} onClick={generatePdf}>
                Generate PDF
              </button>
              {job.kind === "ready" && exportMatchesSelection ? (
                <a className="downloadLink" href={job.downloadUrl} download="spellcards.pdf">Download PDF</a>
              ) : null}
              {job.kind === "paused" ? (
                <button type="button" onClick={resumeChecking}>Resume checking</button>
              ) : null}
            </div>
            <div className="pdfStatus" role="status" aria-live="polite">
              {job.kind === "submitting" ? <p>Submitting PDF export…</p> : null}
              {job.kind === "checking" ? <p>{job.status === "QUEUED" ? "PDF queued…" : "Generating PDF…"}</p> : null}
              {job.kind === "ready" && exportMatchesSelection ? <p>PDF ready.</p> : null}
              {job.kind === "failed" || job.kind === "paused" ? <p className="error">{job.message}</p> : null}
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}
