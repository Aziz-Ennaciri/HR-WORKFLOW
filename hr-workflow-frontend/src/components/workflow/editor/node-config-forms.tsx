"use client";

import { Field } from "@/components/ui/field";
import { Input, Select, Textarea } from "@/components/ui/input";
import { useUsersByRole } from "@/hooks/queries";
import { SECRET_MASK, type GptProvider, type NodeConfigs } from "@/types";

interface FormProps<T> {
  nodeId: string;
  config: T;
  onChange: (config: T) => void;
}

const fid = (nodeId: string, field: string) => `node-${nodeId}-${field}`;

export function EmailConfigForm({ nodeId, config, onChange }: FormProps<NodeConfigs["EMAIL"]>) {
  return (
    <>
      <Field id={fid(nodeId, "to")} label="To" required hint="Separate several addresses with commas.">
        <Input type="text" inputMode="email" placeholder="hiring@company.com" value={config.to ?? ""} onChange={(e) => onChange({ ...config, to: e.target.value })} />
      </Field>
      <Field id={fid(nodeId, "subject")} label="Subject" required>
        <Input value={config.subject ?? ""} onChange={(e) => onChange({ ...config, subject: e.target.value })} />
      </Field>
      <Field id={fid(nodeId, "body")} label="Message" hint="The previous step's results are added below your message.">
        <Textarea rows={4} value={config.body ?? ""} onChange={(e) => onChange({ ...config, body: e.target.value })} />
      </Field>
    </>
  );
}

const MODELS: Record<GptProvider, string[]> = {
  ollama: ["llama3.2:3b", "llama3.1:8b", "mistral"],
  openai: ["gpt-4o-mini", "gpt-4o"],
  anthropic: ["claude-sonnet-4-20250514", "claude-3-5-haiku-latest"],
};

const STYLES = [
  { value: 0.2, label: "Precise" },
  { value: 0.7, label: "Balanced" },
  { value: 1.0, label: "Creative" },
];

export function GptConfigForm({ nodeId, config, onChange }: FormProps<NodeConfigs["GPT"]>) {
  const provider = config.provider ?? "ollama";
  const needsKey = provider !== "ollama";
  const hasStoredKey = config.apiKey === SECRET_MASK;

  return (
    <>
      <Field id={fid(nodeId, "prompt")} label="Instructions" required hint="What the AI should do with the previous step's data (e.g. the CVs read from Drive).">
        <Textarea rows={5} placeholder="Rank these candidates for a senior Java role and explain each score." value={config.prompt ?? ""} onChange={(e) => onChange({ ...config, prompt: e.target.value })} />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field id={fid(nodeId, "provider")} label="Provider">
          <Select
            value={provider}
            onChange={(e) => {
              const next = e.target.value as GptProvider;
              // A model only makes sense for its own provider
              onChange({ ...config, provider: next, model: MODELS[next][0], apiKey: next === "ollama" ? undefined : config.apiKey });
            }}
          >
            <option value="ollama">Ollama (local)</option>
            <option value="openai">OpenAI</option>
            <option value="anthropic">Anthropic</option>
          </Select>
        </Field>
        <Field id={fid(nodeId, "model")} label="Model">
          <Select value={config.model ?? MODELS[provider][0]} onChange={(e) => onChange({ ...config, model: e.target.value })}>
            {[...new Set([...(config.model ? [config.model] : []), ...MODELS[provider]])].map((model) => (
              <option key={model} value={model}>
                {model}
              </option>
            ))}
          </Select>
        </Field>
      </div>
      {needsKey && (
        <Field
          id={fid(nodeId, "api-key")}
          label="API key"
          required
          hint={hasStoredKey ? "A key is stored (encrypted). Type a new one to replace it." : "Stored encrypted; never shown again."}
        >
          <Input
            type="password"
            autoComplete="off"
            placeholder={hasStoredKey ? "•••••••• (stored)" : "sk-…"}
            value={hasStoredKey ? "" : (config.apiKey ?? "")}
            onChange={(e) => onChange({ ...config, apiKey: e.target.value || (hasStoredKey ? SECRET_MASK : undefined) })}
          />
        </Field>
      )}
      <div className="grid grid-cols-2 gap-3">
        <Field id={fid(nodeId, "style")} label="Response style">
          <Select value={String(config.temperature ?? 0.2)} onChange={(e) => onChange({ ...config, temperature: Number(e.target.value) })}>
            {STYLES.map((style) => (
              <option key={style.value} value={style.value}>
                {style.label}
              </option>
            ))}
          </Select>
        </Field>
        <Field id={fid(nodeId, "format")} label="Output">
          <Select value={config.outputFormat ?? "json"} onChange={(e) => onChange({ ...config, outputFormat: e.target.value as "text" | "json" })}>
            <option value="json">Table (for Excel)</option>
            <option value="text">Text</option>
          </Select>
        </Field>
      </div>
    </>
  );
}

export function DriveConfigForm({ nodeId, config, onChange }: FormProps<NodeConfigs["DRIVE"]>) {
  const action = config.action ?? "read";
  return (
    <>
      <Field id={fid(nodeId, "action")} label="Action">
        <Select value={action} onChange={(e) => onChange({ ...config, action: e.target.value as "read" | "write" })}>
          <option value="read">Read all files in a folder</option>
          <option value="write">Save the previous step&apos;s output as a file</option>
        </Select>
      </Field>
      <Field id={fid(nodeId, "folder")} label="Folder" required hint={<>A folder inside the storage directory, e.g. <span className="font-mono">cvs</span>.</>}>
        <Input placeholder="cvs" value={config.folderId ?? ""} onChange={(e) => onChange({ ...config, folderId: e.target.value })} />
      </Field>
      {action === "write" && (
        <Field id={fid(nodeId, "file")} label="File name" hint="Leave empty to generate one.">
          <Input placeholder="results.txt" value={config.fileName ?? ""} onChange={(e) => onChange({ ...config, fileName: e.target.value })} />
        </Field>
      )}
    </>
  );
}

export function ExcelConfigForm({ nodeId, config, onChange }: FormProps<NodeConfigs["EXCEL"]>) {
  const reading = config.operation === "READ";
  return (
    <>
      <Field id={fid(nodeId, "operation")} label="Action">
        <Select
          value={reading ? "READ" : "WRITE"}
          onChange={(e) => onChange({ operation: e.target.value as "READ" | "WRITE", fileName: "", folderId: "" })}
        >
          <option value="WRITE">Generate a report from the previous step</option>
          <option value="READ">Read rows from a spreadsheet</option>
        </Select>
      </Field>
      {reading ? (
        <>
          <Field id={fid(nodeId, "folder")} label="Folder" hint="Folder inside the storage directory; leave empty for the root.">
            <Input placeholder="reports" value={config.folderId ?? ""} onChange={(e) => onChange({ ...config, folderId: e.target.value })} />
          </Field>
          <Field id={fid(nodeId, "file")} label="File" required hint=".xlsx or .csv">
            <Input placeholder="employees.xlsx" value={config.fileName ?? ""} onChange={(e) => onChange({ ...config, fileName: e.target.value })} />
          </Field>
        </>
      ) : (
        <Field id={fid(nodeId, "report")} label="Report name" required hint="Downloadable from the execution and linked in emails.">
          <Input placeholder="Candidate ranking" value={config.fileName ?? ""} onChange={(e) => onChange({ ...config, operation: "WRITE", fileFormat: "XLSX", fileName: e.target.value })} />
        </Field>
      )}
    </>
  );
}

export function ApprovalConfigForm({ nodeId, config, onChange }: FormProps<NodeConfigs["APPROVAL"]>) {
  const { data: admins, isLoading } = useUsersByRole("ROLE_ADMIN");
  return (
    <>
      <Field id={fid(nodeId, "approver")} label="Approver" required hint="This person must approve before the workflow continues.">
        {admins && admins.length > 0 ? (
          <Select value={config.approverEmail ?? ""} onChange={(e) => onChange({ ...config, approverEmail: e.target.value })}>
            <option value="">Select an approver…</option>
            {admins.map((user) => (
              <option key={user.id} value={user.email}>
                {[user.firstName, user.lastName].filter(Boolean).join(" ") || user.email} ({user.email})
              </option>
            ))}
          </Select>
        ) : (
          <Input
            type="email"
            disabled={isLoading}
            placeholder={isLoading ? "Loading approvers…" : "manager@company.com"}
            value={config.approverEmail ?? ""}
            onChange={(e) => onChange({ ...config, approverEmail: e.target.value })}
          />
        )}
      </Field>
      <Field id={fid(nodeId, "instructions")} label="Instructions for the approver">
        <Textarea rows={3} placeholder="Check the shortlist meets the role requirements." value={config.instructions ?? ""} onChange={(e) => onChange({ ...config, instructions: e.target.value })} />
      </Field>
    </>
  );
}
