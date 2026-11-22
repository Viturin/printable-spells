import assert from "node:assert/strict";
import { mkdir, open } from "node:fs/promises";
import { resolve } from "node:path";
import { chromium } from "playwright";

const [baseUrl = "http://127.0.0.1:4173", outputDir = "out/visual-preview"] = process.argv.slice(2);
const output = (name) => resolve(outputDir, name);

async function waitForCard(canvas) {
  await canvas.scrollIntoViewIfNeeded();
  await canvas.locator('xpath=self::canvas[@data-rendered="true"]').waitFor();
}

async function exportPdf(page, layout, filename) {
  await page.getByLabel("PDF layout").selectOption(layout);
  await page.getByRole("button", { name: "Generate PDF" }).click();
  const [download] = await Promise.all([
    page.waitForEvent("download"),
    page.getByRole("link", { name: "Download PDF" }).click(),
  ]);
  await download.saveAs(output(filename));
  const file = await open(output(filename));
  try {
    const signature = Buffer.alloc(5);
    await file.read(signature);
    assert.equal(signature.toString(), "%PDF-", filename + " must be a PDF");
  } finally {
    await file.close();
  }
}

async function capture(browser, name, viewport) {
  const page = await browser.newPage({ viewport, reducedMotion: "reduce" });
  page.setDefaultTimeout(45_000);
  page.on("console", (message) => console.log(`[${name}] ${message.type()}: ${message.text()}`));
  page.on("pageerror", (error) => console.error(`[${name}]`, error));
  page.on("response", (response) => {
    if (response.status() >= 400) console.error(`[${name}] HTTP ${response.status()} ${response.url()}`);
  });
  page.on("requestfailed", (request) => {
    const reason = request.failure()?.errorText;
    if (reason !== "net::ERR_ABORTED") console.error(`[${name}] ${reason} ${request.url()}`);
  });

  try {
    await page.goto(baseUrl);
    for (const spell of ["Insect Form", "Daze"]) {
      await page.getByRole("searchbox", { name: "Search spells" }).fill(spell);
      await page.getByRole("button", { name: `Add ${spell}`, exact: true }).click();
    }

    const grid = page.getByRole("list", { name: "Spell card previews" });
    await grid.waitFor();
    const tiles = grid.getByRole("listitem");
    assert.ok(await tiles.count() >= 4, "Insect Form should produce at least three cards");
    for (const tile of await tiles.all()) {
      await waitForCard(tile.locator("canvas"));
    }

    await grid.evaluate((element) => { element.scrollTop = 0; });
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({ path: output(`${name}.png`), fullPage: true, animations: "disabled" });

    if (name === "desktop") {
      await grid.screenshot({ path: output("card-grid.png"), animations: "disabled" });
      await tiles.nth(2).screenshot({ path: output("continuation-card.png") });
      await exportPdf(page, "PLAYING_CARD", "playing-cards.pdf");
      await exportPdf(page, "A4_SHEET", "a4-cut-sheets.pdf");

      await page.getByRole("checkbox", { name: "Double-sided" }).check();
      await grid.getByRole("button", { name: "Spell card 1, front side. Show back" }).click();
      const flipped = grid.getByRole("button", { name: "Spell card 1, back side. Show front" });
      await waitForCard(flipped.locator(".pdfCardBack canvas"));
      await flipped.screenshot({ path: output("duplex-back.png"), animations: "disabled" });
    }
  } catch (error) {
    await page.screenshot({ path: output(`${name}-failure.png`), timeout: 8_000 })
      .catch((screenshotError) => console.error("Could not save failure screenshot", screenshotError));
    throw error;
  } finally {
    await page.close();
  }
}

await mkdir(outputDir, { recursive: true });
const browser = await chromium.launch({ executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE });
try {
  await capture(browser, "desktop", { width: 1440, height: 900 });
  await capture(browser, "mobile", { width: 390, height: 844 });
} finally {
  await browser.close();
}
