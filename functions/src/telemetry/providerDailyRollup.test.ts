import { expect } from 'chai';
import { buildProviderDailyRollups } from './providerDailyRollup';
import { RawTelemetryDoc } from './providerDailyRollupTypes';

describe('providerDailyRollup', () => {
  it('TEL_015 provider daily rollup detects cache hit rate changes by app version', () => {
    const rows: RawTelemetryDoc[] = [
      {
        providerKey: 'google',
        providerLabel: 'Google',
        modelId: 'gemini-2.5-flash',
        modelLabel: 'gemini-2.5-flash',
        appVersionName: '1.3.0',
        appVersionCode: 130,
        telemetrySchemaVersion: 1,
        inputTokens: 1000,
        outputTokens: 0,
        reasoningTokens: 0,
        cacheReadInputTokens: 700,
        cacheWriteInputTokens: 0,
        cacheCreationInputTokens: 0,
        costUsdDecimal: null,
        requestStartedAtMs: 0,
        firstOutputTokenAtMs: null,
        completedAtMs: 0,
      },
      {
        providerKey: 'google',
        providerLabel: 'Google',
        modelId: 'gemini-2.5-flash',
        modelLabel: 'gemini-2.5-flash',
        appVersionName: '1.3.0',
        appVersionCode: 130,
        telemetrySchemaVersion: 1,
        inputTokens: 1000,
        outputTokens: 0,
        reasoningTokens: 0,
        cacheReadInputTokens: 500,
        cacheWriteInputTokens: 0,
        cacheCreationInputTokens: 0,
        costUsdDecimal: null,
        requestStartedAtMs: 0,
        firstOutputTokenAtMs: null,
        completedAtMs: 0,
      },
      {
        providerKey: 'google',
        providerLabel: 'Google',
        modelId: 'gemini-2.5-flash',
        modelLabel: 'gemini-2.5-flash',
        appVersionName: '1.4.0',
        appVersionCode: 140,
        telemetrySchemaVersion: 1,
        inputTokens: 1000,
        outputTokens: 0,
        reasoningTokens: 0,
        cacheReadInputTokens: 100,
        cacheWriteInputTokens: 0,
        cacheCreationInputTokens: 0,
        costUsdDecimal: null,
        requestStartedAtMs: 0,
        firstOutputTokenAtMs: null,
        completedAtMs: 0,
      },
      {
        providerKey: 'google',
        providerLabel: 'Google',
        modelId: 'gemini-2.5-flash',
        modelLabel: 'gemini-2.5-flash',
        appVersionName: '1.4.0',
        appVersionCode: 140,
        telemetrySchemaVersion: 1,
        inputTokens: 1000,
        outputTokens: 0,
        reasoningTokens: 0,
        cacheReadInputTokens: 0,
        cacheWriteInputTokens: 0,
        cacheCreationInputTokens: 0,
        costUsdDecimal: null,
        requestStartedAtMs: 0,
        firstOutputTokenAtMs: null,
        completedAtMs: 0,
      },
    ];

    const rollups = buildProviderDailyRollups('2026-05-10', rows);
    const oldVersion = rollups.find(
      (rollup) =>
        rollup.providerLabel === 'Google' &&
        rollup.modelLabel === 'gemini-2.5-flash' &&
        rollup.appVersionName === '1.3.0',
    );
    const newVersion = rollups.find(
      (rollup) =>
        rollup.providerLabel === 'Google' &&
        rollup.modelLabel === 'gemini-2.5-flash' &&
        rollup.appVersionName === '1.4.0',
    );

    expect(oldVersion?.requestCount).to.equal(2);
    expect(oldVersion?.inputTokens).to.equal(2000);
    expect(oldVersion?.cacheReadInputTokens).to.equal(1200);
    expect(oldVersion?.cacheHitRatePercent).to.equal(60.0);
    expect(newVersion?.requestCount).to.equal(2);
    expect(newVersion?.inputTokens).to.equal(2000);
    expect(newVersion?.cacheReadInputTokens).to.equal(100);
    expect(newVersion?.cacheHitRatePercent).to.equal(5.0);
  });

  it('Anthropic cache hit rate uses non-cached plus cache-read input', () => {
    const rows: RawTelemetryDoc[] = [
      {
        providerKey: 'anthropic',
        providerLabel: 'Anthropic',
        modelId: 'claude-sonnet-4-20250514',
        modelLabel: 'claude-sonnet-4-20250514',
        appVersionName: '1.4.0',
        appVersionCode: 140,
        telemetrySchemaVersion: 1,
        inputTokens: 7787,
        outputTokens: 1515,
        reasoningTokens: 0,
        cacheReadInputTokens: 16000,
        cacheWriteInputTokens: 0,
        cacheCreationInputTokens: 0,
        costUsdDecimal: null,
        requestStartedAtMs: 0,
        firstOutputTokenAtMs: null,
        completedAtMs: 0,
      },
    ];

    const rollups = buildProviderDailyRollups('2026-05-10', rows);
    const anthropic = rollups.find((rollup) => rollup.providerLabel === 'Anthropic');

    expect(anthropic?.cacheHitRatePercent).to.equal(67.3);
  });
});
