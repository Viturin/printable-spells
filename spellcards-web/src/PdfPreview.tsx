import { useEffect, useRef, useState } from "react";
import { getDocument, GlobalWorkerOptions } from "pdfjs-dist";
import type { PDFDocumentProxy, RenderTask } from "pdfjs-dist";
import workerUrl from "pdfjs-dist/build/pdf.worker.min.mjs?url";

GlobalWorkerOptions.workerSrc = workerUrl;

export default function PdfPreview({ url }: { url: string }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [document, setDocument] = useState<PDFDocumentProxy>();
  const [pageNumber, setPageNumber] = useState(1);
  const [error, setError] = useState("");

  useEffect(() => {
    const task = getDocument({ url });
    let disposed = false;
    task.promise.then(
      (pdf) => { if (!disposed) setDocument(pdf); },
      () => { if (!disposed) setError("Could not display the PDF preview."); },
    );
    return () => {
      disposed = true;
      void task.destroy();
    };
  }, [url]);

  useEffect(() => {
    if (!document) return;
    let disposed = false;
    let rendering: RenderTask | undefined;
    async function drawPage(pdf: PDFDocumentProxy) {
      try {
        const page = await pdf.getPage(pageNumber);
        if (disposed || !canvas.current) return;
        const viewport = page.getViewport({ scale: 2 });
        canvas.current.style.setProperty("--pdf-page-width", page.getViewport({ scale: 1.6 }).width + "px");
        canvas.current.width = Math.ceil(viewport.width);
        canvas.current.height = Math.ceil(viewport.height);
        rendering = page.render({ canvas: canvas.current, viewport });
        await rendering.promise;
      } catch {
        if (!disposed) setError("Could not display this PDF page.");
      }
    }
    void drawPage(document);
    return () => {
      disposed = true;
      rendering?.cancel();
    };
  }, [document, pageNumber]);

  return (
    <section className="pdfPreview" aria-label="Generated spell cards PDF">
      {error ? <p className="emptyState error" role="status">{error} Open the PDF in a new tab to view it.</p> : null}
      {!document && !error ? <p className="emptyState" role="status">Loading PDF preview…</p> : null}
      {document && !error ? (
        <>
          {document.numPages > 1 ? (
            <nav className="pdfPageControls" aria-label="PDF pages">
              <button type="button" disabled={pageNumber === 1}
                onClick={() => setPageNumber(pageNumber - 1)}>Previous</button>
              <span aria-live="polite">{pageNumber} / {document.numPages}</span>
              <button type="button" disabled={pageNumber === document.numPages}
                onClick={() => setPageNumber(pageNumber + 1)}>Next</button>
            </nav>
          ) : null}
          <div className="pdfPreviewPage">
            <canvas ref={canvas} role="img" aria-label={"Spell cards PDF page " + pageNumber} />
          </div>
        </>
      ) : null}
    </section>
  );
}
