export interface RawTelemetryDoc {
  providerKey: string;
  providerLabel: string;
  modelId: string;
  modelLabel: string;
  appVersionName: string;
  appVersionCode: number | null;
  telemetrySchemaVersion: number | null;
  inputTokens: number | null;
  outputTokens: number | null;
  reasoningTokens: number | null;
  cacheReadInputTokens: number | null;
  cacheWriteInputTokens: number | null;
  cacheCreationInputTokens: number | null;
  costUsdDecimal: string | null;
  requestStartedAtMs: number;
  firstOutputTokenAtMs: number | null;
  completedAtMs: number | null;
}

export interface ProviderDailyRollupDoc {
  dateUtc: string;
  providerKey: string;
  providerLabel: string;
  modelId: string;
  modelLabel: string;
  appVersionName: string;
  appVersionCode: number | null;
  telemetrySchemaVersion: number | null;
  requestCount: number;
  turnCount: number;
  inputTokens: number;
  outputTokens: number;
  reasoningTokens: number;
  cacheReadInputTokens: number;
  cacheWriteInputTokens: number;
  cacheCreationInputTokens: number;
  costUsdDecimal: string;
  ttftSampleCount: number;
  ttftMsTotal: number;
  generationDurationMsTotal: number;
  cacheHitRatePercent: number;
}
