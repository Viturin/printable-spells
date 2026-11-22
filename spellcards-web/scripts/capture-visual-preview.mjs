import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdir, open, rename } from "node:fs/promises";
import { resolve } from "node:path";
import { promisify } from "node:util";
import { chromium } from "playwright";

const [baseUrl = "http://127.0.0.1:4173", outputDir = "out/visual-preview"] = process.argv.slice(2);
const output = (name) => resolve(outputDir, name);
const run = promisify(execFile);
const gallerySpells = ["Heal", "Thunderstrike", "Gentle Landing"];

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

async function captureA4Duplex(page) {
  const pdf = "a4-duplex.pdf";
  await exportPdf(page, "A4_SHEET", pdf);

  const { stdout } = await run("pdfinfo", ["-f", "1", "-l", "2", output(pdf)]);
  assert.match(stdout, /^Pages:\s+2$/m, "Heal should produce one front and one back sheet");
  for (const pageNumber of [1, 2]) {
    const size = new RegExp(String.raw`^Page\s+${pageNumber}\s+size:.* \(A4\)\r?$`, "m");
    assert.match(stdout, size, `Sheet ${pageNumber} must be A4`);
  }

  await run("pdftoppm", ["-r", "125", "-png", output(pdf), output("a4-duplex")]);
  await rename(output("a4-duplex-1.png"), output("a4-duplex-front.png"));
  await rename(output("a4-duplex-2.png"), output("a4-duplex-back.png"));
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
    await page.getByRole("checkbox", { name: "Double-sided" }).uncheck();
    const search = page.getByRole("searchbox", { name: "Search spells" });
    for (const spell of gallerySpells) {
      await search.fill(spell);
      await page.getByRole("button", { name: `Add ${spell}`, exact: true }).click();
    }

    const grid = page.getByRole("list", { name: "Spell card previews" });
    await grid.waitFor();
    const tiles = grid.getByRole("listitem");
    assert.equal(await tiles.count(), 4, "The preview gallery should fit on four card faces");
    for (const tile of await tiles.all()) {
      await waitForCard(tile.locator("canvas"));
    }

    await grid.evaluate((element) => { element.scrollTop = 0; });
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({ path: output(`${name}.png`), fullPage: true, animations: "disabled" });

    if (name === "desktop") {
      await grid.screenshot({ path: output("card-grid.png"), animations: "disabled" });
      await exportPdf(page, "PLAYING_CARD", "playing-cards.pdf");
      await exportPdf(page, "A4_SHEET", "a4-cut-sheets.pdf");

      // Keep the print example to one physical card, independent of the gallery.
      for (const spell of gallerySpells.slice(1)) {
        await page.getByRole("button", { name: `Remove ${spell}`, exact: true }).click();
      }
      await grid.waitFor();
      assert.equal(await tiles.count(), 2, "Heal should produce exactly two card faces");
      await waitForCard(tiles.nth(1).locator("canvas"));
      await tiles.nth(1).screenshot({ path: output("continuation-card.png") });

      await page.getByRole("checkbox", { name: "Double-sided" }).check();
      await grid.getByRole("button", { name: "Spell card 1, front side. Show back" }).click();
      const flipped = grid.getByRole("button", { name: "Spell card 1, back side. Show front" });
      await waitForCard(flipped.locator(".pdfCardBack canvas"));
      await flipped.screenshot({ path: output("duplex-back.png"), animations: "disabled" });
      await captureA4Duplex(page);
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
