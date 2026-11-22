import { useEffect, useState } from "react";
import type {
  CreateSpellCardJobRequest,
  CreateSpellCardJobResponse,
  ErrorResponse,
  SpellCardJobResponse,
} from "./generated/client/src/models";

const jobsUrl = "/api/v1/spellcards/jobs";
const previewDebounceMs = 650;
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

export function useSpellCardPdfJob(request: CreateSpellCardJobRequest) {
  const [attempt, setAttempt] = useState(0);
  const hasSpells = request.spellNames.length > 0;
  const requestBody = JSON.stringify(request);
  const requestKey = JSON.stringify([requestBody, attempt]);
  const [result, setResult] = useState<{ key: string; job: PdfJob }>();
  const job: PdfJob = result?.key === requestKey ? result.job
    : hasSpells ? { kind: "submitting" } : { kind: "idle" };
  const pollingJobId = job.kind === "checking" ? job.id : null;

  useEffect(() => {
    setResult(undefined);
    if (!hasSpells) {
      return;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      try {
        const response = await fetch(jobsUrl, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: requestBody,
          signal: AbortSignal.any([controller.signal, AbortSignal.timeout(requestTimeoutMs)]),
        });
        if (!response.ok) {
          throw new Error(await responseMessage(response, "Could not submit the PDF job."));
        }
        const created: CreateSpellCardJobResponse = await response.json();
        if (!controller.signal.aborted) {
          setResult({ key: requestKey, job: { kind: "checking", id: created.jobId, status: "QUEUED" } });
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          const message = error instanceof Error ? error.message : "Could not submit the PDF job.";
          setResult({ key: requestKey, job: { kind: "failed", message } });
        }
      }
    }, previewDebounceMs);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [hasSpells, requestBody, requestKey]);

  useEffect(() => {
    if (pollingJobId === null) {
      return;
    }
    const controller = new AbortController();
    let timer: number;
    let pendingPolls = 0;

    function update(next: PdfJob) {
      setResult((current) => current?.key === requestKey
        && current.job.kind === "checking" && current.job.id === pollingJobId
        ? { key: requestKey, job: next } : current);
    }

    function pause(message: string) {
      setResult((current) => current?.key === requestKey
        && current.job.kind === "checking" && current.job.id === pollingJobId
        ? { key: requestKey, job: { ...current.job, kind: "paused", message } } : current);
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
          update({ kind: "failed", message: "This PDF job is no longer available." });
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
            update({ kind: "checking", id: result.jobId, status: result.status });
            pendingPolls++;
            if (pendingPolls >= 150) {
              pause("PDF generation is taking longer than expected.");
            } else {
              timer = window.setTimeout(poll, pollIntervalMs);
            }
            break;
          case "COMPLETED":
            if (result.downloadUrl) {
              update({ kind: "ready", id: result.jobId, downloadUrl: result.downloadUrl });
            } else {
              pause("The PDF download link is unavailable.");
            }
            break;
          case "FAILED":
            update({ kind: "failed", message: result.errorMessage || "PDF generation failed." });
            break;
          case "EXPIRED":
            update({ kind: "failed", message: "This PDF has expired. Generate it again to download it." });
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
  }, [pollingJobId, requestKey]);

  function resumeChecking() {
    setResult((current) => current?.key === requestKey && current.job.kind === "paused"
      ? { key: requestKey, job: { kind: "checking", id: current.job.id, status: current.job.status } }
      : current);
  }

  return { job, retry: () => setAttempt((current) => current + 1), resumeChecking };
}
