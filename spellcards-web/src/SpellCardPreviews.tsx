import type { SpellSummary } from "./generated/client/src/models";

function SpellCardPreview({ spell }: { spell: SpellSummary }) {
  const preview = spell.cardPreview;
  const fallbackRules = [
    { label: "Cast", value: spell.actionCost },
    { label: "Range", value: spell.range },
    { label: "Targets", value: spell.target },
    { label: "Traditions", value: spell.traditions.join(", ") },
    { label: "Traits", value: spell.traits.join(", ") },
  ];
  const rules = preview?.rules ?? fallbackRules.filter(({ value }) => value);

  return (
    <article className="cardFace" data-kind={spell.kind}>
      <header className="cardFaceHeader">
        <h3>{spell.name}</h3>
        {preview?.action ? (
          <span className="cardFaceAction" role="img" aria-label={"Casting cost: " + spell.actionCost}>
            <span aria-hidden="true">{preview.action.symbols}</span>
            <span aria-hidden="true">{preview.action.caption}</span>
          </span>
        ) : null}
      </header>
      <p className="cardFaceType">
        {preview?.classification ?? [spell.kind, "Rank " + spell.level, spell.rarity].join(" · ")}
      </p>
      <div className="cardFaceContent">
        {rules.length > 0 ? (
          <dl className="cardFaceRules">
            {rules.map(({ label, value }) => (
              <div key={label}>
                <dt>{label}</dt>{" "}<dd>{value}</dd>
              </div>
            ))}
          </dl>
        ) : null}
        {spell.description ? (
          <div className="cardFaceDescription">
            {spell.description.split(/\n{2,}/).map((paragraph, index) => (
              <p key={index}>{paragraph}</p>
            ))}
          </div>
        ) : null}
      </div>
    </article>
  );
}

export function SpellCardPreviews({ spells, onRemove }: {
  spells: SpellSummary[];
  onRemove: (id: string) => void;
}) {
  return (
    <ul className="previewList">
      {spells.map((spell) => (
        <li key={spell.id}>
          <SpellCardPreview spell={spell} />
          <button type="button" className="previewRemove" aria-label={"Remove " + spell.name}
            onClick={() => onRemove(spell.id)}>
            Remove
          </button>
        </li>
      ))}
    </ul>
  );
}
