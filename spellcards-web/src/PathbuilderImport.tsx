import { useState } from "react";
import type { ResolveSpellNamesResponse, SpellSummary } from "./generated/client/src/models";
import { readPathbuilderSpellNames } from "./pathbuilder";

export function PathbuilderImport({ onImport }: { onImport: (spells: SpellSummary[]) => void }) {
  const [json, setJson] = useState("");
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<ResolveSpellNamesResponse | null>(null);
  const [error, setError] = useState("");

  async function importSpells() {
    setResult(null);
    setError("");
    try {
      const names = readPathbuilderSpellNames(json);
      if (names.length === 0) {
        setError("This export has no spell names to import.");
        return;
      }

      setImporting(true);
      const response = await fetch("/api/v1/spells/resolve", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ names }),
        signal: AbortSignal.timeout(10000),
      });
      if (!response.ok) {
        throw new Error("Could not check the spell catalog. Try importing again.");
      }

      const imported: ResolveSpellNamesResponse = await response.json();
      onImport(imported.items);
      setResult(imported);
      setJson("");
    } catch (failure) {
      setError(failure instanceof Error && failure.name === "Error"
        ? failure.message
        : "Could not check the spell catalog. Try importing again.");
    } finally {
      setImporting(false);
    }
  }

  return (
    <details className="pathbuilderImport">
      <summary>Import Pathbuilder JSON</summary>
      <form onSubmit={(event) => {
        event.preventDefault();
        void importSpells();
      }}>
        <label htmlFor="pathbuilder-json">Paste your Pathbuilder character export</label>
        <textarea
          id="pathbuilder-json"
          value={json}
          onChange={(event) => setJson(event.target.value)}
          spellCheck={false}
          autoComplete="off"
          disabled={importing}
          rows={5}
        />
        <button type="submit" disabled={importing || !json.trim()}>
          {importing ? "Matching spells…" : "Import spells"}
        </button>
        {error ? <p className="error" role="alert">{error}</p> : null}
        {result ? (
          <div className="importResult">
            <p role="status">
              {result.items.length === 0
                ? "No spells matched the catalog."
                : result.items.length === 1
                  ? "1 matched spell is in Selected spells."
                  : result.items.length + " matched spells are in Selected spells."}
            </p>
            {result.unmatchedNames.length ? (
              <>
                <p>Not found in the catalog ({result.unmatchedNames.length})</p>
                <ul>
                  {result.unmatchedNames.map((name) => <li key={name}>{name}</li>)}
                </ul>
              </>
            ) : null}
          </div>
        ) : null}
      </form>
    </details>
  );
}
