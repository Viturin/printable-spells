import { useEffect, useRef, useState } from "react";
import { getDocument, GlobalWorkerOptions } from "pdfjs-dist";
import type { PDFDocumentProxy, RenderTask } from "pdfjs-dist";
import workerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?url";

GlobalWorkerOptions.workerSrc = workerUrl;

function CardPage({ pdf, pageNumber, decorative = false }: { pdf: PDFDocumentProxy; pageNumber: number; decorative?: boolean }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    const target = canvas.current;
    if (!target) return;

    let disposed = false;
    let rendering: RenderTask | undefined;
    async function draw() {
      try {
        const page = await pdf.getPage(pageNumber);
        if (disposed || !target) return;
        const viewport = page.getViewport({ scale: 2.5 });
        target.width = Math.ceil(viewport.width);
        target.height = Math.ceil(viewport.height);
        rendering = page.render({ canvas: target, viewport });
        await rendering.promise;
      } catch {
        if (!disposed) setError(true);
      }
    }

    const observer = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return;
      observer.disconnect();
      void draw();
    }, { rootMargin: "250px" });
    observer.observe(target);
    return () => {
      disposed = true;
      observer.disconnect();
      rendering?.cancel();
    };
  }, [pdf, pageNumber]);

  return error ? <span className="emptyState error">Could not display this side.</span>
    : <canvas ref={canvas} aria-hidden={decorative || undefined}
        role={decorative ? undefined : "img"} aria-label={decorative ? undefined : "Spell card " + pageNumber} />;
}

function FlipCard({ pdf, cardNumber }: { pdf: PDFDocumentProxy; cardNumber: number }) {
  const [back, setBack] = useState(false);
  return (
    <button type="button" className="pdfCardFlip"
      aria-label={`Spell card ${cardNumber}, ${back ? "back" : "front"} side. Show ${back ? "front" : "back"}`}
      onClick={() => setBack((visible) => !visible)}>
      <span className="pdfCardFaces" data-back={back}>
        <span className="pdfCardSide" aria-hidden={back}>
          <CardPage pdf={pdf} pageNumber={2 * cardNumber - 1} decorative />
        </span>
        <span className="pdfCardSide pdfCardBack" aria-hidden={!back}>
          <CardPage pdf={pdf} pageNumber={2 * cardNumber} decorative />
        </span>
      </span>
    </button>
  );
}

export default function PdfPreview({ url, doubleSided }: { url: string; doubleSided: boolean }) {
  const [pdf, setPdf] = useState<PDFDocumentProxy>();
  const [error, setError] = useState(false);

  useEffect(() => {
    const task = getDocument({ url });
    let disposed = false;
    task.promise.then(
      (document) => { if (!disposed) setPdf(document); },
      () => { if (!disposed) setError(true); },
    );
    return () => {
      disposed = true;
      void task.destroy();
    };
  }, [url]);

  if (error) {
    return <p className="emptyState error" role="status">Could not display the card preview.{" "}
      <a href={url} target="_blank" rel="noopener">Open preview PDF</a>
    </p>;
  }
  if (!pdf) return <p className="emptyState" role="status">Loading card previews…</p>;

  if (doubleSided && pdf.numPages % 2 !== 0) {
    return <p className="emptyState error" role="status">The duplex preview is missing a matching card side.</p>;
  }

  const cards = doubleSided ? pdf.numPages / 2 : pdf.numPages;
  return (
    <>
      {doubleSided ? <p className="pdfFlipHint">Select any card to flip it.</p> : null}
      <ol className="pdfCardGrid" aria-label="Spell card previews">
        {Array.from({ length: cards }, (_, index) => (
          <li key={index + 1} className="pdfCardTile">
            {doubleSided
              ? <FlipCard pdf={pdf} cardNumber={index + 1} />
              : <CardPage pdf={pdf} pageNumber={index + 1} />}
          </li>
        ))}
      </ol>
    </>
  );
}
