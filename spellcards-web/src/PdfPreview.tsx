import { useEffect, useRef, useState } from "react";
import { getDocument, GlobalWorkerOptions } from "pdfjs-dist";
import type { PDFDocumentProxy, RenderTask } from "pdfjs-dist";
import workerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?url";

GlobalWorkerOptions.workerSrc = workerUrl;

function CardPage({ pdf, pageNumber }: { pdf: PDFDocumentProxy; pageNumber: number }) {
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

  return (
    <li className="pdfCardTile">
      {error ? <p className="emptyState error">Could not display card {pageNumber}.</p>
        : <canvas ref={canvas} role="img" aria-label={"Spell card " + pageNumber} />}
    </li>
  );
}

export default function PdfPreview({ url }: { url: string }) {
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

  return (
    <ol className="pdfCardGrid" aria-label="Spell card previews">
      {Array.from({ length: pdf.numPages }, (_, index) => (
        <CardPage key={index + 1} pdf={pdf} pageNumber={index + 1} />
      ))}
    </ol>
  );
}
