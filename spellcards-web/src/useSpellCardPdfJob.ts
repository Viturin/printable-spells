import { useEffect, useRef, useState } from "react";
import type {
  CreateSpellCardJobResponse,
  ErrorResponse,
  SpellCardJobResponse,
} from "./generated/client/src/models";

const jobsUrl = "/api/v1/spellcards/jobs";
const pollIntervalMs = 2000;
const requestTimeoutMs = 10000;

type PendingJob = { id: string; status: "QUEUED" | "PROCESSING" };

type PdfJob =
  | { kind: "idle" }
  | { kind: "submitting" }
  | ({ kind: "checking" } & PendingJob)
  | ({ kind: "paused"; message: string } & PendingJob)
  | { kind: "ready"; id: string; downloadUrl: string }
  | { kind: "failed"; message: string };

async function responseMessage(response: Response, fallback: string): Promise<string> {
  if (response.headers.get("content-type")?.includes("application/json")) {
    const error = (await response.json().catch(() => null)) as ErrorResponse | null;
    return error?.message || fallback;
  }
  return fallback;
}

export function useSpellCardPdfJob() {
  const [job, setJob] = useState<PdfJob>({ kind: "idle" });
  const submission = useRef<AbortController | null>(null);
  const downloadedJobId = useRef<string | null>(null);
  const pollingJobId = job.kind === "checking" ? job.id : null;

  useEffect(() => () => submission.current?.abort(), []);

  useEffect(() => {
    if (pollingJobId === null) {
      return;
    }
    const controller = new AbortController();
    let timer: number;
    let pendingPolls = 0;

    function pause(message: string) {
      setJob((current) =>
        current.kind === "checking" && current.id === pollingJobId
          ? { ...current, kind: "paused", message }
          : current,
      );
    }

    async function poll() {
      try {
        const response = await fetch(jobsUrl + "/" + pollingJobId, {
          signal: AbortSignal.any([controller.signal, AbortSignal.timeout(requestTimeoutMs)]),
        });
        if (controller.signal.aborted) {
          return;
        }
        if (response.status === 404) {
          setJob({ kind: "failed", message: "This PDF job is no longer available." });
          return;
        }
        if (!response.ok) {
          pause(await responseMessage(response, "Could not check PDF status."));
          return;
        }
        const result: SpellCardJobResponse = await response.json();
        if (controller.signal.aborted) {
          return;
        }
        switch (result.status) {
          case "QUEUED":
          case "PROCESSING":
            setJob({ kind: "checking", id: result.jobId, status: result.status });
            pendingPolls++;
            if (pendingPolls >= 150) {
              pause("PDF generation is taking longer than expected.");
            } else {
              timer = window.setTimeout(poll, pollIntervalMs);
            }
            break;
          case "COMPLETED":
            if (result.downloadUrl) {
              setJob({ kind: "ready", id: result.jobId, downloadUrl: result.downloadUrl });
            } else {
              pause("The PDF download link is unavailable.");
            }
            break;
          case "FAILED":
            setJob({ kind: "failed", message: result.errorMessage || "PDF generation failed." });
            break;
          case "EXPIRED":
            setJob({ kind: "failed", message: "This PDF has expired. Generate it again to download it." });
            break;
          default:
            pause("The PDF job returned an unknown status.");
        }
      } catch {
        if (!controller.signal.aborted) {
          pause("Could not check PDF status. You can resume checking this job.");
        }
      }
    }

    timer = window.setTimeout(poll, pollIntervalMs);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [pollingJobId]);

  useEffect(() => {
    if (job.kind !== "ready" || downloadedJobId.current === job.id) {
      return;
    }
    downloadedJobId.current = job.id;
    const link = document.createElement("a");
    link.href = job.downloadUrl;
    link.download = "spellcards.pdf";
    link.hidden = true;
    document.body.append(link);
    link.click();
    link.remove();
  }, [job]);

  const active = job.kind === "submitting" || job.kind === "checking" || job.kind === "paused";

  async function generate(spellNames: string[]) {
    if (active || submission.current !== null || spellNames.length === 0) {
      return;
    }
    const controller = new AbortController();
    submission.current = controller;
    setJob({ kind: "submitting" });
    try {
      const response = await fetch(jobsUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ spellNames }),
        signal: AbortSignal.any([controller.signal, AbortSignal.timeout(requestTimeoutMs)]),
      });
      if (!response.ok) {
        throw new Error(await responseMessage(response, "Could not submit the PDF job."));
      }
      const result: CreateSpellCardJobResponse = await response.json();
      if (!controller.signal.aborted) {
        setJob({ kind: "checking", id: result.jobId, status: "QUEUED" });
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        const message = error instanceof Error ? error.message : "Could not submit the PDF job.";
        setJob({ kind: "failed", message });
      }
    } finally {
      submission.current = null;
    }
  }

  function resumeChecking() {
    setJob((current) => current.kind === "paused"
      ? { kind: "checking", id: current.id, status: current.status }
      : current);
  }

  function stopChecking() {
    setJob({ kind: "idle" });
  }

  return { job, active, generate, resumeChecking, stopChecking };
}
