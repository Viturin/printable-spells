import type { SpellSummary } from "./generated/client/src/models";

const diceLabels = new Set(["Caster attack", "Target save", "Effect dice", "Heightening"]);

function SpellCardPreview({ spell }: { spell: SpellSummary }) {
  const preview = spell.cardPreview;
  const fallbackRules = [
    { label: "Cast", value: spell.actionCost },
    { label: "Range", value: spell.range, symbol: "↔" },
    { label: "Targets", value: spell.target },
    { label: "Traditions", value: spell.traditions.join(", "), symbol: "✦" },
    { label: "Traits", value: spell.traits.filter((trait) => trait.toLowerCase() !== "cantrip").join(", ") },
  ];
  const rules = preview?.rules ?? fallbackRules.filter(({ value }) => value);
  const traitRule = rules.find(({ label }) => label === "Traits");
  const traits = traitRule?.value?.split(/,\s*/).filter(Boolean) ?? [];
  const diceHeader = rules.find(({ label }) => label === "Dice needed");
  const visibleRules = rules.filter(({ label }) => label !== "Traits" && label !== "Dice needed" && !diceLabels.has(label));
  const dice = rules.filter(({ label }) => diceLabels.has(label));

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
      {diceHeader?.value ? (
        <ul className="cardFaceDiceHeader" aria-label="Dice needed">
          {diceHeader.value.split(/ {2,}/).map((die) => {
            const [symbol, label] = die.split(" ");
            return <li key={label}><span aria-hidden="true">{symbol}</span> {label}</li>;
          })}
        </ul>
      ) : null}
      <div className="cardFaceContent">
        {visibleRules.length > 0 ? (
          <dl className="cardFaceRules">
            {visibleRules.map(({ label, value, symbol }) => (
              <div className="cardFaceRule" key={label}>
                <dt>
                  {symbol ? <span className="cardFaceRuleSymbol" role="img" aria-label={label} title={label}>{symbol}</span> : label + ":"}
                </dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
        ) : null}
        {traits.length > 0 ? (
          <ul className="cardFaceTraits" aria-label="Spell traits">
            {traits.map((trait) => <li key={trait}>{trait}</li>)}
          </ul>
        ) : null}
        {dice.length > 0 ? (
          <dl className="cardFaceDice" aria-label="Spell dice">
            {dice.map(({ label, value }) => (
              <div key={label}><dt>{label}:</dt>{" "}<dd>{value}</dd></div>
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
