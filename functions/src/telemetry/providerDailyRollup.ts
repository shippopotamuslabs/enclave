import * as functions from 'firebase-functions/v1';
import { ProviderDailyRollupDoc, RawTelemetryDoc } from './providerDailyRollupTypes';

type RollupKey = string;

function rollupKey(row: RawTelemetryDoc): RollupKey {
  return [
    row.providerLabel,
    row.modelLabel,
    row.appVersionName ?? 'unknown',
  ].join('|');
}

export function buildProviderDailyRollups(
  _dateUtc: string,
  rows: RawTelemetryDoc[],
): ProviderDailyRollupDoc[] {
  const grouped = new Map<RollupKey, ProviderDailyRollupDoc>();

  rows.forEach((row) => {
    const key = rollupKey(row);
    const existing =
      grouped.get(key) ??
      ({
        dateUtc: _dateUtc,
        providerKey: row.providerKey,
        providerLabel: row.providerLabel,
        modelId: row.modelId,
        modelLabel: row.modelLabel,
        appVersionName: row.appVersionName ?? 'unknown',
        appVersionCode: row.appVersionCode ?? null,
        telemetrySchemaVersion: row.telemetrySchemaVersion ?? null,
        requestCount: 0,
        turnCount: 0,
        inputTokens: 0,
        outputTokens: 0,
        reasoningTokens: 0,
        cacheReadInputTokens: 0,
        cacheWriteInputTokens: 0,
        cacheCreationInputTokens: 0,
        costUsdDecimal: '0',
        ttftSampleCount: 0,
        ttftMsTotal: 0,
        generationDurationMsTotal: 0,
        cacheHitRatePercent: 0,
      } satisfies ProviderDailyRollupDoc);

    existing.requestCount += 1;
    existing.inputTokens += row.inputTokens ?? 0;
    existing.outputTokens += row.outputTokens ?? 0;
    existing.reasoningTokens += row.reasoningTokens ?? 0;
    existing.cacheReadInputTokens += row.cacheReadInputTokens ?? 0;
    existing.cacheWriteInputTokens += row.cacheWriteInputTokens ?? 0;
    existing.cacheCreationInputTokens += row.cacheCreationInputTokens ?? 0;
    if (row.costUsdDecimal != null) {
      const prior = Number.parseFloat(existing.costUsdDecimal);
      const next = Number.parseFloat(row.costUsdDecimal);
      existing.costUsdDecimal = (prior + next).toFixed(6);
    }
    grouped.set(key, existing);
  });

  return Array.from(grouped.values()).map((rollup) => ({
    ...rollup,
    cacheHitRatePercent:
      rollup.inputTokens === 0
        ? 0
        : Number(
            ((rollup.cacheReadInputTokens / rollup.inputTokens) * 100).toFixed(1),
          ),
  }));
}

/** Callable entry point for admin rollup refresh; Firestore scan wiring is deferred. */
export const rebuildProviderDailyRollups = functions.https.onCall(async () => ({
  status: 'noop',
}));
