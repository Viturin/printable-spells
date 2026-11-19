function asObject(value: unknown): Record<string, unknown> | undefined {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : undefined;
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

export function readPathbuilderSpellNames(json: string): string[] {
  let parsed: unknown;
  try {
    parsed = JSON.parse(json.trim());
  } catch {
    throw new Error("Could not read the pasted JSON. Copy the full Pathbuilder export and try again.");
  }

  const root = asObject(parsed);
  const exportedBuild = asObject(root?.build);
  const build = exportedBuild ?? root;
  if (!build || root?.success === false ||
      (!exportedBuild && !["spellCasters", "focus", "rituals"].some((key) => key in build))) {
    throw new Error("Paste a Pathbuilder character export.");
  }

  const names = new Map<string, string>();
  function addNames(list: unknown) {
    for (const value of asArray(list)) {
      if (typeof value === "string") {
        const name = value.trim();
        const key = name.toLowerCase();
        if (name && !names.has(key)) {
          names.set(key, name);
        }
      }
    }
  }

  for (const entry of asArray(build.spellCasters)) {
    const caster = asObject(entry);
    for (const rank of [...asArray(caster?.spells), ...asArray(caster?.prepared)]) {
      addNames(asObject(rank)?.list);
    }
  }
  for (const tradition of Object.values(asObject(build.focus) ?? {})) {
    for (const ability of Object.values(asObject(tradition) ?? {})) {
      const focus = asObject(ability);
      addNames(focus?.focusCantrips);
      addNames(focus?.focusSpells);
    }
  }
  addNames(build.rituals);

  return [...names.values()];
}
