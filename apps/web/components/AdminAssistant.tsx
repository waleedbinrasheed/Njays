"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";

type Severity = "INFO" | "WARN" | "CRITICAL";

type Highlight = {
  severity: Severity;
  category: string;
  title: string;
  detail: string;
};

type StuckOrder = {
  publicCode: string;
  status: string;
  age: string;
};

type Snapshot = {
  summary: string;
  highlights: Highlight[];
  ordersActive: number;
  ordersStuckCount: number;
  stuckOrders: StuckOrder[];
  paymentsPendingCount: number;
  pendingAmountPaisa: number;
  bankAwaitingConfirmationCount: number;
  bankAwaitingConfirmationPaisa: number;
  jazzCashOpenCount: number;
  jazzCashExpiringSoonCount: number;
  codPendingCount: number;
  revenueTodayPaisa: number;
  revenueTodayCount: number;
  revenueWeekPaisa: number;
  revenueMonthPaisa: number;
  customActiveCount: number;
  readyActiveCount: number;
  generatedAt: string;
};

type AskResponse = {
  answer: string;
  highlights: Highlight[];
};

type ChatMessage = {
  role: "user" | "assistant";
  text: string;
};

const SUGGESTIONS = [
  "What needs attention today?",
  "Any stuck orders?",
  "Pending payments",
  "Revenue today",
  "JazzCash status",
  "Bank transfers",
];

function severityClass(severity: Severity) {
  if (severity === "CRITICAL") return "highlight-card--critical";
  if (severity === "WARN") return "highlight-card--warn";
  return "highlight-card--info";
}

export function AdminAssistant() {
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [question, setQuestion] = useState("");
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [asking, setAsking] = useState(false);
  const chatEndRef = useRef<HTMLDivElement>(null);

  function loadBriefing() {
    setLoading(true);
    setError("");
    api<Snapshot>("/api/v1/admin/assistant/briefing")
      .then(setSnapshot)
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load insights"))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    loadBriefing();
  }, []);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [messages]);

  async function sendQuestion(text: string) {
    const trimmed = text.trim();
    if (!trimmed || asking) return;
    setMessages((prev) => [...prev, { role: "user", text: trimmed }]);
    setQuestion("");
    setAsking(true);
    try {
      const res = await api<AskResponse>("/api/v1/admin/assistant/ask", {
        method: "POST",
        body: JSON.stringify({ question: trimmed }),
      });
      setMessages((prev) => [...prev, { role: "assistant", text: res.answer }]);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Could not get an answer";
      setMessages((prev) => [...prev, { role: "assistant", text: msg }]);
    } finally {
      setAsking(false);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    sendQuestion(question);
  }

  return (
    <div className="panel assistant-panel">
      <div className="assistant-header">
        <span className="section-label">AI Assistant</span>
        <h3 style={{ margin: "0 0 0.25rem" }}>What&apos;s going on right now</h3>
        <p className="muted" style={{ marginTop: 0 }}>
          Rule-based insights computed live from your orders and payments — no external AI service involved.
        </p>
      </div>

      {loading && <div className="skeleton" aria-busy="true" aria-label="Loading insights" />}
      {error && <div className="error">{error}</div>}

      {snapshot && (
        <>
          <p style={{ marginTop: 0 }}>{snapshot.summary}</p>
          <div className="assistant-highlights">
            {snapshot.highlights.map((h, i) => (
              <div key={i} className={`highlight-card ${severityClass(h.severity)}`}>
                <strong>{h.title}</strong>
                <p>{h.detail}</p>
              </div>
            ))}
          </div>
        </>
      )}

      <div className="assistant-suggestions">
        {SUGGESTIONS.map((s) => (
          <button
            key={s}
            type="button"
            className="suggestion-chip"
            onClick={() => sendQuestion(s)}
            disabled={asking}
          >
            {s}
          </button>
        ))}
      </div>

      {messages.length > 0 && (
        <div className="assistant-chat" role="log" aria-live="polite">
          {messages.map((m, i) => (
            <div key={i} className={`chat-bubble chat-bubble--${m.role}`}>
              {m.text}
            </div>
          ))}
          {asking && <div className="chat-bubble chat-bubble--assistant chat-bubble--typing">Thinking…</div>}
          <div ref={chatEndRef} />
        </div>
      )}

      <form className="assistant-input-row" onSubmit={onSubmit}>
        <input
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="Ask about orders, payments, revenue…"
          aria-label="Ask the admin assistant"
        />
        <button className="btn btn-primary" disabled={asking || !question.trim()}>
          {asking ? "Asking…" : "Ask"}
        </button>
      </form>
    </div>
  );
}
