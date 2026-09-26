"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { api, type Measurement } from "@/lib/api";

type Step = "kameez" | "shalwar" | "style";

const BACK_OPTIONS = [
  { value: "PLAIN", label: "Plain", hint: "Clean back" },
  { value: "BOX", label: "Box", hint: "Box pleat" },
];

const SLEEVE_OPTIONS = [
  { value: "PLAIN", label: "Plain", hint: "Straight sleeve" },
  { value: "CUT", label: "Cut", hint: "Shaped sleeve" },
];

const BUTTON_OPTIONS = [
  { value: "SAME", label: "Same", hint: "Match fabric" },
  { value: "CONTRAST", label: "Contrast", hint: "Different color" },
  { value: "BRASS", label: "Brass", hint: "Metal look" },
];

const COLLAR_OPTIONS = [
  { value: "BAN", label: "Ban", hint: "Classic ban" },
  { value: "HALF_BAN", label: "Half Ban", hint: "Lower stand" },
  { value: "FULL_BAN", label: "Full Ban", hint: "Higher stand" },
  { value: "MANDARIN", label: "Mandarin", hint: "Soft collar" },
];

const CUFF_OPTIONS = [
  { value: "ROUND", label: "Round", hint: "Rounded edge" },
  { value: "CUT", label: "Cut", hint: "Angled cut" },
];

function MeasureInput({
  label,
  hint,
  value,
  onChange,
}: {
  label: string;
  hint?: string;
  value: string;
  onChange: (v: string) => void;
}) {
  const num = value === "" ? 0 : Number(value);

  function bump(delta: number) {
    const next = Math.max(0, Math.round((num + delta) * 10) / 10);
    onChange(String(next));
  }

  return (
    <div className="measure-field">
      <span>
        {label}
        {hint ? <small className="muted"> · {hint}</small> : null}
      </span>
      <div className="controls">
        <button type="button" onClick={() => bump(-0.5)} aria-label={`Decrease ${label}`}>
          −
        </button>
        <input
          type="number"
          step="0.1"
          min="0"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder="0.0"
        />
        <button type="button" onClick={() => bump(0.5)} aria-label={`Increase ${label}`}>
          +
        </button>
      </div>
    </div>
  );
}

function OptionPicker({
  title,
  options,
  value,
  onChange,
}: {
  title: string;
  options: { value: string; label: string; hint: string }[];
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <div className="section-card" style={{ marginBottom: "0.85rem" }}>
      <strong style={{ display: "block", marginBottom: "0.65rem", color: "var(--teal)" }}>{title}</strong>
      <div className="option-grid">
        {options.map((opt) => (
          <button
            key={opt.value}
            type="button"
            className={`option-chip ${value === opt.value ? "active" : ""}`}
            onClick={() => onChange(opt.value)}
          >
            {opt.label}
            <small>{opt.hint}</small>
          </button>
        ))}
      </div>
    </div>
  );
}

export default function MeasurementsPage() {
  const [items, setItems] = useState<Measurement[]>([]);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState("");
  const [step, setStep] = useState<Step>("kameez");
  const [name, setName] = useState("My custom fit");

  const [kameezLength, setKameezLength] = useState("");
  const [chest, setChest] = useState("");
  const [waist, setWaist] = useState("");
  const [hip, setHip] = useState("");
  const [shoulder, setShoulder] = useState("");
  const [sleeveLength, setSleeveLength] = useState("");
  const [collarLength, setCollarLength] = useState("");

  const [shalwarLength, setShalwarLength] = useState("");
  const [shalwarBottom, setShalwarBottom] = useState("");

  const [backStyle, setBackStyle] = useState("PLAIN");
  const [sleeveStyle, setSleeveStyle] = useState("PLAIN");
  const [buttonStyle, setButtonStyle] = useState("SAME");
  const [collarStyle, setCollarStyle] = useState("BAN");
  const [cuffStyle, setCuffStyle] = useState("ROUND");
  const [notes, setNotes] = useState("");

  function load() {
    api<Measurement[]>("/api/v1/me/measurements")
      .then(setItems)
      .catch((e) => setError(e.message));
  }

  useEffect(() => {
    load();
  }, []);

  const progress = useMemo(() => {
    if (step === "kameez") return 1;
    if (step === "shalwar") return 2;
    return 3;
  }, [step]);

  function toNum(v: string) {
    return v.trim() === "" ? null : Number(v);
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setSaved("");
    try {
      await api("/api/v1/me/measurements", {
        method: "POST",
        body: JSON.stringify({
          name,
          unit: "INCH",
          kameezLength: toNum(kameezLength),
          chest: toNum(chest),
          waist: toNum(waist),
          hip: toNum(hip),
          shoulder: toNum(shoulder),
          sleeveLength: toNum(sleeveLength),
          collarLength: toNum(collarLength),
          shalwarLength: toNum(shalwarLength),
          shalwarBottom: toNum(shalwarBottom),
          backStyle,
          sleeveStyle,
          buttonStyle,
          collarStyle,
          cuffStyle,
          notes,
          isDefault: true,
        }),
      });
      setSaved("Measurement profile saved.");
      setStep("kameez");
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed");
    }
  }

  return (
    <section className="container section">
      <div className="page-header">
        <span className="section-label">Fit</span>
        <h2>Your measurements</h2>
        <p className="lead">Three quick sections — Kameez, Shalwar, then style options.</p>
      </div>

      <div className="stepper" role="tablist" aria-label="Measurement steps">
        <button
          type="button"
          role="tab"
          aria-selected={step === "kameez"}
          className={`stepper-item ${step === "kameez" ? "active" : progress > 1 ? "done" : ""}`}
          onClick={() => setStep("kameez")}
        >
          1 · Kameez
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={step === "shalwar"}
          className={`stepper-item ${step === "shalwar" ? "active" : progress > 2 ? "done" : ""}`}
          onClick={() => setStep("shalwar")}
        >
          2 · Shalwar
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={step === "style"}
          className={`stepper-item ${step === "style" ? "active" : ""}`}
          onClick={() => setStep("style")}
        >
          3 · Others
        </button>
      </div>

      <div className="measure-grid">
        <form className="panel form" onSubmit={onSubmit}>
          <label>
            Profile name
            <input value={name} onChange={(e) => setName(e.target.value)} required />
          </label>

          {step === "kameez" && (
            <div className="section-card">
              <h3 style={{ marginTop: 0, color: "var(--teal)" }}>Kameez</h3>
              <div className="form-row">
                <MeasureInput label="Length" value={kameezLength} onChange={setKameezLength} />
                <MeasureInput label="Chest" value={chest} onChange={setChest} />
              </div>
              <div className="form-row">
                <MeasureInput label="Waist" hint="West" value={waist} onChange={setWaist} />
                <MeasureInput label="Hips" value={hip} onChange={setHip} />
              </div>
              <div className="form-row">
                <MeasureInput label="SH" hint="Shoulder" value={shoulder} onChange={setShoulder} />
                <MeasureInput label="SL" hint="Sleeve length" value={sleeveLength} onChange={setSleeveLength} />
              </div>
              <MeasureInput label="CL" hint="Collar length" value={collarLength} onChange={setCollarLength} />
              <button type="button" className="btn btn-primary" onClick={() => setStep("shalwar")}>
                Next: Shalwar →
              </button>
            </div>
          )}

          {step === "shalwar" && (
            <div className="section-card">
              <h3 style={{ marginTop: 0, color: "var(--teal)" }}>Shalwar</h3>
              <div className="form-row">
                <MeasureInput label="Length" value={shalwarLength} onChange={setShalwarLength} />
                <MeasureInput label="Bottom" value={shalwarBottom} onChange={setShalwarBottom} />
              </div>
              <div className="form-actions">
                <button type="button" className="btn btn-ghost" onClick={() => setStep("kameez")}>
                  ← Kameez
                </button>
                <button type="button" className="btn btn-primary" onClick={() => setStep("style")}>
                  Next: Style options →
                </button>
              </div>
            </div>
          )}

          {step === "style" && (
            <div>
              <h3 style={{ marginTop: 0, color: "var(--teal)" }}>Others / Style</h3>
              <OptionPicker title="Back" options={BACK_OPTIONS} value={backStyle} onChange={setBackStyle} />
              <OptionPicker title="Sleeves" options={SLEEVE_OPTIONS} value={sleeveStyle} onChange={setSleeveStyle} />
              <OptionPicker title="Button" options={BUTTON_OPTIONS} value={buttonStyle} onChange={setButtonStyle} />
              <OptionPicker title="Collar" options={COLLAR_OPTIONS} value={collarStyle} onChange={setCollarStyle} />
              <OptionPicker title="Cuff" options={CUFF_OPTIONS} value={cuffStyle} onChange={setCuffStyle} />
              <label>
                Notes
                <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={3} />
              </label>
              <div className="form-actions">
                <button type="button" className="btn btn-ghost" onClick={() => setStep("shalwar")}>
                  ← Shalwar
                </button>
                <button className="btn btn-primary">Save profile</button>
              </div>
            </div>
          )}

          {error && <div className="error">{error}</div>}
          {saved && <p className="success">{saved}</p>}
        </form>

        <div className="panel preview-sticky">
          <h3 style={{ marginTop: 0 }}>Live preview</h3>
          <div className="section-card" style={{ marginBottom: "0.8rem" }}>
            <strong>Kameez</strong>
            <div className="muted">
              L {kameezLength || "—"} · Chest {chest || "—"} · Waist {waist || "—"} · Hips {hip || "—"}
            </div>
            <div className="muted">
              SH {shoulder || "—"} · SL {sleeveLength || "—"} · CL {collarLength || "—"}
            </div>
          </div>
          <div className="section-card" style={{ marginBottom: "0.8rem" }}>
            <strong>Shalwar</strong>
            <div className="muted">
              Length {shalwarLength || "—"} · Bottom {shalwarBottom || "—"}
            </div>
          </div>
          <div className="section-card" style={{ marginBottom: "1rem" }}>
            <strong>Style</strong>
            <div className="muted">
              Back {backStyle} · Sleeve {sleeveStyle} · Button {buttonStyle}
            </div>
            <div className="muted">
              Collar {collarStyle} · Cuff {cuffStyle}
            </div>
          </div>

          <h3>Saved profiles</h3>
          {items.length === 0 && <p className="muted">No profiles yet.</p>}
          {items.map((m) => (
            <div key={m.id} className="list-row" style={{ flexDirection: "column", alignItems: "stretch" }}>
              <strong>{m.name}</strong>
              <div className="muted">
                Kameez L {m.kameezLength || "—"} · Chest {m.chest || "—"} · SH {m.shoulder || "—"}
              </div>
              <div className="muted">
                Shalwar {m.shalwarLength || "—"} / Bottom {m.shalwarBottom || "—"} · {m.backStyle || "—"} back ·{" "}
                {m.cuffStyle || "—"} cuff
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
