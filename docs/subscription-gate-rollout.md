# Subscription Gate Rollout Runbook (ENC-244)

Deploy the subscription gate without locking yourself out. Order: billing
functions first, override doc second, live cutover third, enforcement last.
Client stays usable for you at every step via the override doc.

Summary: (1) ship Stripe billing functions with test secrets and prove the
webhook writes entitlement docs; (2) plant override docs so you and testers
are immune before any gate goes live; (3) cut Stripe to live and prove one
real payment round-trips; then (4) deploy the gated callables + rules and
flip R2 private in a single sitting, harden the consoles, and smoke-test
every carve-out. Why this order: rules without the webhook strand paying
users; functions without your override strand you.

## Preconditions

- [ ] PRs merged to main in this order: ENC-236 (entitlement core: `users/{uid}/entitlement/{stripe,override}` docs, `assertEntitled`), ENC-237 (Stripe webhook + checkout/portal sessions writing the entitlement doc), ENC-238 (Firestore rules gate on the sync surface), ENC-239 (Storage rules gate on user paths), ENC-240 (callable asserts: `uploadSharedChat`, `backfillSyncFeedCallable`), ENC-241 (client entitlement-aware sync gate, no retry churn), ENC-243 (gated `mintModelDownloadUrl` + telemetry upload gate).
- [ ] Tools: Firebase CLI logged into project `enclave-497101` (repo-pinned `firebase-tools` 14.27.0 at `functions/node_modules/.bin/firebase`). Stripe Dashboard access (test + live). Cloudflare dashboard access (R2 bucket behind `config.enclave-app.com`). GCP console access (`enclave-497101`).
- [ ] Work from repo root `/home/sean/Code/enclave` on branch `shippopotamus/enc-244-...` (stacked; ENC-236..243 code present).
- [ ] Verify CLI auth first: `./functions/node_modules/.bin/firebase projects:list` shows `enclave-497101`. If not, run `./functions/node_modules/.bin/firebase login` and `firebase use enclave-497101`. Every deploy command below passes `-P enclave-497101` explicitly so the wrong project can never be targeted.
- [ ] Verify the tree builds before you start: `npm --prefix functions run build` (the `predeploy` hook in `firebase.json` runs this on every functions deploy; a local green run avoids a failed deploy halfway through enforcement day).
- [ ] Keep two browser windows ready throughout: Firebase console for `enclave-497101` (Firestore Data, Authentication, Functions logs) and Stripe Dashboard with the Test/Live mode toggle visible. Every step below says which one to look at.
- [ ] Agree with yourself on the enforcement-day timebox: STEPS 0–1 can be any day; STEP 2 must be one sitting because between the functions deploy and the rules deploy the gate is half-on. Do not start STEP 2 within an hour of any Stripe price or webhook change.

## STEP 0 — Capture rollback artifacts

Do this before deploying anything. If rules get you locked out, these files are the way back.

- [ ] Save currently deployed rules (the CLI has no `firestore:rules:get` — verified absent in firebase-tools 14.27.0 — so use the console): Firebase console > Firestore Database > Rules > copy the full text into `~/enclave-rollback/<date>/firestore.rules.deployed.bak`. Same for Storage: Storage > Rules > copy into `~/enclave-rollback/<date>/storage.rules.deployed.bak`. (Keep rollback artifacts OUTSIDE the repo: `rollback/` is NOT gitignored — verified via `git check-ignore`, which matches nothing inside the checkout — so never write them inside the repo.)
- [ ] Save currently deployed functions list: `./functions/node_modules/.bin/firebase functions:list -P enclave-497101 > ~/enclave-rollback/<date>/functions-list-before.txt`.
- [ ] Note the pre-gate commit SHA for functions rollback: `git log --oneline -- functions firestore.rules storage.rules` and record the newest SHA that predates ENC-240. Call it `<PRE_GATE_SHA>` below.
- [ ] Expected outputs to confirm STEP 0 is complete: `functions:list` exits 0 and lists the currently deployed functions (compare against the export list in `functions/src/index.ts` — any function deployed but missing from the list below means a stale deploy you should know about); both `.bak` files are non-empty and contain `rules_version = '2'`; `<PRE_GATE_SHA>` is written down somewhere outside this repo (paper, notes app) so a broken checkout cannot strand you.
- [ ] Confirm the deploy path works end-to-end BEFORE enforcement day: run `firebase deploy -P enclave-497101 --only functions:stripeWebhook --dry-run` (validates build + config without touching production). The `--dry-run` flag is documented in `firebase deploy --help` and may still enable APIs — that is harmless.

## STEP 1a — Deploy billing functions (test mode)

Deploy ONLY the three billing functions so nothing is gated yet.

- [ ] Set test-mode secrets (exact names from `functions/src/billing/checkoutSessions.ts:14`, `stripeWebhook.ts:8`):
  `firebase functions:secrets:set STRIPE_SECRET_KEY` (paste test `sk_test_...`)
  `firebase functions:secrets:set STRIPE_WEBHOOK_SECRET` (paste test `whsec_...`, after creating the endpoint below — set a placeholder first, re-set after)
- [ ] Create `functions/.env.enclave-497101` (plain, non-secret env read via `process.env` in `checkoutSessions.ts:18-26`; firebase-tools dotenv loads this file at deploy):
  `STRIPE_PRICE_ID=<test price id>`
  (Optional overrides; defaults already point at `https://shippopotamuslabs.com/enclave/subscribe` + `?checkout=success` / `?checkout=cancel`: `ENCLAVE_CHECKOUT_SUCCESS_URL`, `ENCLAVE_CHECKOUT_CANCEL_URL`, `ENCLAVE_PORTAL_RETURN_URL`.)
- [ ] Deploy: `firebase deploy -P enclave-497101 --only functions:stripeWebhook,functions:createCheckoutSession,functions:createPortalSession`
- [ ] In Stripe Dashboard (Test mode) > Developers > Webhooks > Add endpoint: `https://us-central1-enclave-497101.cloudfunctions.net/stripeWebhook` (default v1 region; no `region()` in code — confirm exact URL in Firebase console > Functions > `stripeWebhook` after deploy). Select events `checkout.session.completed`, `customer.subscription.updated`, `customer.subscription.deleted`, `invoice.payment_failed` — the exact set the handler switches on in `functions/src/billing/billing.ts` (`:217`, `:247-248`, `:281`; verified against the source). Copy the signing secret and re-run `functions:secrets:set STRIPE_WEBHOOK_SECRET`.
- [ ] Test-mode E2E: start a test checkout from the app (calls `createCheckoutSession`, opens the returned Stripe URL), pay with card `4242 4242 4242 4242` (any future expiry, any CVC).
- [ ] Confirm the webhook fired: Stripe Dashboard (Test) > Developers > Webhooks > click the endpoint > recent delivery shows HTTP 200 for `checkout.session.completed`. If it shows 400, the signature secret is wrong — re-set `STRIPE_WEBHOOK_SECRET`.
- [ ] Confirm the entitlement doc: Firebase console > Firestore > `users/{uid}/entitlement/stripe` shows `active: true` plus `stripeCustomerId`/`stripeSubscriptionId` fields (written by `verifyAndHandleWebhook` in `functions/src/billing/billing.ts`).
- [ ] Confirm the app sees it: kill and restart the app, Settings subscription row no longer shows "Not subscribed".
- [ ] Confirm cancel path: in the app open Settings > subscription > manage (calls `createPortalSession`), cancel the test subscription. Re-check the doc: `active: false`. App restart -> "Not subscribed" returns and sync stops without logcat retry spam.
- [ ] Check Functions logs for the run: `firebase functions:log -P enclave-497101 --only stripeWebhook` shows the 200 delivery and no `webhook secret is not configured` error (`stripeWebhook.ts:21`).

## STEP 1b — Write your override doc (do this NOW, before any rules deploy)

- [ ] Find your uid: Firebase console > Authentication > Users > find your Google account row > copy the UID column. Call it `<SEAN_UID>`. If you have multiple sign-in methods, use the uid of the account actually signed in on your device (check in-app Settings > account row if unsure).
- [ ] Create the doc path step by step: Firestore console > Start collection `users` > document id `<SEAN_UID>` (if your user doc does not exist yet, create it with one dummy field, e.g. `created: true` — the `users/{uid}` rule allows owner write) > Start collection `entitlement` > document id `override` > add field `freeAccess`, type boolean, value `true` > Save.
- [ ] Firestore console > create document `users/<SEAN_UID>/entitlement/override` with field `freeAccess` = boolean `true`. (`OVERRIDE_ENTITLEMENT_DOC_ID = 'override'`, `freeAccess === true` grants access in `functions/src/entitlement/assertEntitled.ts`.)
- [ ] Repeat for each named tester uid.
- [ ] Verify: in the app signed in as you, Settings no longer shows "Not subscribed".
- [ ] Sanity-check the override covers every gate, not just sync: the override doc satisfies `isUserEntitled` (all callables incl. `mintModelDownloadUrl`) AND the rules `isEntitled` (Firestore + Storage) AND `assertOverrideEntitled` (rollups). One doc, all three.
- [ ] Do NOT delete your own `stripe` doc if 1a/1c testing created one — the override wins regardless (`isEntitledFromDocs` checks override first, `assertEntitled.ts:31-43`), and keeping both proves the precedence works.

## STEP 1c — Switch Stripe to live

- [ ] Set live secrets: `firebase functions:secrets:set STRIPE_SECRET_KEY` (live `sk_live_...`), then redeploy the same three named functions as in 1a.
- [ ] Set live price: update `STRIPE_PRICE_ID` in `functions/.env.enclave-497101` to the live price id, redeploy the same three named functions.
- [ ] Live mode webhook: Stripe Dashboard (Live mode) > new endpoint with the SAME function URL, same events. Set live `whsec_...` via `functions:secrets:set STRIPE_WEBHOOK_SECRET`, redeploy `functions:stripeWebhook`.
- [ ] Verify the live endpoint independently of the app: Stripe Dashboard (Live) > Developers > Webhooks > click the endpoint > Send test webhook > `checkout.session.completed` > expect a 200 with outcome text (unknown/test events return 200 without writes per `stripeWebhook.ts` header comment — a 200 here proves signature verification works, not that a doc was written).
- [ ] Keep the TEST endpoint and test secrets recorded but disabled: set the test endpoint to disabled in Stripe (do not delete it — you will re-enable it for future billing work). The deployed function reads whichever secret is current, so there is exactly one active webhook secret at a time.
- [ ] One real checkout (your account) -> entitlement doc `active:true` -> portal cancel -> `active:false` -> refund the charge in Stripe Dashboard > Payments. (Cancel flips the doc; refund returns the money.)

## STEP 1d — Deploy `mintModelDownloadUrl` with R2 credentials

Bucket stays public at this point, so nothing breaks yet.

- [ ] Set secrets (exact names from `functions/src/models/modelDownloadUrl.ts:146`): `firebase functions:secrets:set R2_ACCESS_KEY_ID`, `firebase functions:secrets:set R2_SECRET_ACCESS_KEY`.
- [ ] Add to `functions/.env.enclave-497101`: `R2_ENDPOINT=<R2 S3 endpoint>`, `R2_BUCKET=<bucket>`. (Both read in `readModelDownloadConfig`, `modelDownloadUrl.ts:86-94`.)
- [ ] Deploy: `firebase deploy -P enclave-497101 --only functions:mintModelDownloadUrl`
- [ ] Verify the callable rejects cleanly without config: covered by unit tests (`modelDownloadUrl.test.ts`), but live-check the negative path — call it as a non-entitled test user and expect `permission-denied` BEFORE any R2 error. If you instead get `failed-precondition` ("Model downloads are not configured"), the `.env` file did not load — check the filename matches `functions/.env.enclave-497101` exactly (`<projectId>` suffix, not `.env.production`) and redeploy.
- [ ] Verify the happy path as yourself (override-entitled): request a presigned URL for a real model object key and `curl -o /dev/null -w "%{http_code}" "<returned-url>"` -> 200. This proves R2 credentials + endpoint + bucket are all correct while the bucket is still public.
- [ ] Note the TTL: presigned URLs expire after `MODEL_DOWNLOAD_URL_TTL_SECONDS` (`modelDownloadUrl.ts`); clients must not cache them across sessions. No action — just know that a stale-URL download failure after enforcement day means the client held the URL too long, not that R2 broke.

## STEP 1e — Distribute client build (ENC-241 + ENC-243)

- [ ] Build/install a release-signed APK containing ENC-241 (entitlement-aware sync gate) and ENC-243 (client calls `mintModelDownloadUrl`; HF downloads unchanged). Sideload to your device.
- [ ] When ENC-248 distribution pipeline exists, use it instead of manual sideload.
- [ ] Build the exact artifact to sideload: `./gradlew :app:assembleRelease` (unsigned release exercises the R8 path; install manually). Confirm the installed build contains the gate: Settings shows the subscription row with "Not subscribed" on a fresh account (ENC-241 UI, `SettingsScreen.kt:710`).
- [ ] Keep the previous (pre-gate) APK file on hand next to the new one. If the new build misbehaves for reasons unrelated to the gate, reinstalling the old APK is faster than any server rollback.
- [ ] Testers get the same APK plus their override docs from STEP 1b — never distribute the build before their overrides exist, or their first launch will look broken.

## STEP 2 — Enforcement day (single sitting, in this order)

- [ ] Re-confirm `users/<SEAN_UID>/entitlement/override` = `{freeAccess:true}`. Do not proceed without it.
- [ ] Deploy gated callables: `firebase deploy -P enclave-497101 --only functions:uploadSharedChat,functions:backfillSyncFeedCallable,functions:rebuildProviderDailyRollups`
  (`uploadSharedChat` and `backfillSyncFeedCallable` assert any entitlement; `rebuildProviderDailyRollups` asserts override-only — see `providerDailyRollup.ts:98-106`.)
- [ ] Deploy rules: `firebase deploy -P enclave-497101 --only firestore:rules` then `firebase deploy -P enclave-497101 --only storage`. (Target names come from `firebase.json`: the `firestore.rules` key and the `storage` block. Do NOT use `--only firestore` bare — that also pushes indexes; rules-only is the minimal blast radius.)
- [ ] Watch each deploy's output for `Deploy complete!` and zero `Error:` lines. A rules compile error aborts that deploy without touching production — read the error, fix the `.rules` file, redeploy. Rules deploy in seconds; if either hangs over 5 minutes, Ctrl-C and check Firebase status before retrying — never stack two deploys of the same target.
- [ ] Cloudflare: make R2 bucket model objects non-public, keeping exactly these two public at bucket root: `model_config.json`, `trusted_domains.json`. Client URLs are hardcoded to `https://config.enclave-app.com/model_config.json` and `.../trusted_domains.json` (`DynamicModelUrlProvider.kt:22-38`), so those exact URLs must keep returning 200. Either (a) custom-domain path rule that keeps the two JSON paths public, or (b) move the two JSONs to a separate public bucket served at those exact URLs.
- [ ] Option (a) walkthrough: Cloudflare dashboard > R2 > bucket > Settings > Custom Domains confirms `config.enclave-app.com` is attached; then create the narrowest public exception your plan supports that matches ONLY `/model_config.json` and `/trusted_domains.json`, and set everything else (model weight paths — the object keys built by `buildR2ObjectKey` in `core/data/.../download/remote/DynamicModelUrlProvider.kt:76` (server-side mirror is `validateModelDownloadFilePath` in `functions/src/models/modelDownloadUrl.ts:54-84`)) to require signed access. There is no per-object CLI for this in the runbook — do it in the dashboard and screenshot the before/after.
- [ ] Option (b) walkthrough: create a second R2 bucket (e.g. `enclave-config-public`), copy the two JSONs to its root, attach it to the SAME custom domain path space so the exact URLs resolve, then flip the model bucket fully private. Only pick (b) if (a) cannot express per-path exceptions on your plan.
- [ ] Verify the flip from an anonymous context (logged-out browser or `curl` without credentials): a model object URL -> 401/403/denied; both JSON URLs -> 200 with valid JSON bodies. Do all four curls before touching the client: two must fail, two must succeed.
- [ ] Confirm your device still syncs (override), then confirm a fresh non-subscriber device does not (see STEP 4).
- [ ] Between the functions deploy and the rules deploy, do NOT test with the non-subscriber device yet — a half-on gate gives misleading results. Run STEP 4 only after both deploys plus the Cloudflare flip.
- [ ] After the Cloudflare flip, force-stop and restart the app on your device and trigger a model download: it must go through the callable path (`DynamicModelUrlProvider` -> `mintModelDownloadUrl`), not a cached public URL. Clear the app's download cache first if a model was fetched while the bucket was public.
- [ ] After the rules deploy, open Firebase console > Firestore > Rules > confirm the published timestamp is now and the text contains `isEntitled`. Same check under Storage > Rules. This catches the classic mistake of deploying from the wrong directory (rules deploy reads `firebase.json` `firestore.rules`/`storage.rules` paths relative to where you run it — always repo root).

## STEP 3 — Console hardening

- [ ] Android API key restriction: GCP console > APIs & Services > Credentials > Android key for `enclave-497101` > restrict to package `com.browntowndev.enclave` with BOTH SHA-1 fingerprints from `app/google-services.json`: `7E:63:C4:AA:BC:EC:AB:67:A0:8E:CA:37:98:2D:0D:7F:93:E0:4D:0A` and `AC:81:DA:E3:E7:F4:17:93:A1:39:1D:5E:2A:EB:C0:FF:CC:6A:CB:B9` (colon-separated form of the `certificate_hash` values).
- [ ] Which fingerprint is which: the first (`7E:63...`) and second (`AC:81...`) are the two `android_info` `oauth_client` entries in `app/google-services.json` (debug and release keystores). Adding only one breaks sign-in on builds signed with the other — add both, then verify BOTH a debug and a release build can sign in.
- [ ] Build a release APK, install, verify Google sign-in + sync work on the release build specifically. Debug-build sign-in passing is not sufficient — the release keystore hash is the one most likely to be missing.
- [ ] Budget alert: GCP console > Billing > Budgets & alerts > create budget for `enclave-497101` (suggested: monthly actual-spend alert at a number you pick — e.g. 2x expected Functions/Firestore/R2 spend — email to yourself).
- [ ] Set the alert thresholds as percentages, not dollars, so they survive price changes: 50% (info — early warning that enforcement is driving real usage), 100% (action — investigate before overspend), 150% (urgent — likely a retry loop or abuse; check Functions invocations for `stripeWebhook` retry storms first).
- [ ] Scope the budget to the right services: include Cloud Functions, Firestore, and Cloud Storage. Exclude anything unrelated if the project shares billing with other experiments.
- [ ] Firebase console > Authentication > Sign-in method: Google provider only, no others enabled.
- [ ] Stripe Dashboard (Live) > Settings > Billing > make sure test clocks / test data are gone and the live price id matches the `STRIPE_PRICE_ID` you deployed in 1c. A mismatch here is the #1 cause of "user paid but app says Not subscribed".

## STEP 4 — Smoke checklist

- [ ] Your account: cloud sync uploads and downloads work; share create + share view work.
- [ ] Fresh non-subscriber account: no sync (nothing uploaded/downloaded); Settings shows "Not subscribed"; logcat shows no retry loop. Run this exact probe (tags from `CloudSyncTrigger.kt` / `SyncWorker.kt:22`):
  `adb logcat -c; adb logcat | grep -i "enclave_sync\|CloudSyncTrigger\|SyncWorker\|StartCloudSync"`
  then trigger a manual sync in-app. Healthy non-subscriber output: one `Skipping sync enqueue: subscription required` line (`CloudSyncTrigger.kt:28`) and nothing else — specifically NO repeating `Enqueuing enclave_sync (APPEND_OR_REPLACE)` lines and no `StartCloudSyncResult` retry bursts. That single-gate-no-churn behavior is the ENC-241 contract (`StartCloudSyncResult.SubscriptionRequired` stops cleanly).
- [ ] Leave the probe running 5 minutes on the non-subscriber device. Any periodic re-enqueue without user action is a retry-loop bug — stop and fix-forward on the client before continuing.
- [ ] Subscribe via website (`ENCLAVE_SUBSCRIBE_URL` = `https://shippopotamuslabs.com/enclave/subscribe`, `SettingsRoute.kt:15`) -> unlock/app restart -> sync starts.
- [ ] Portal cancel -> `users/{uid}/entitlement/stripe.active:false` -> sync stops.
- [ ] Direct probes (expect denies): Firestore REST read of another user's doc with non-subscriber ID token -> `PERMISSION_DENIED`:
  `curl -H "Authorization: Bearer <NON_SUB_ID_TOKEN>" "https://firestore.googleapis.com/v1/projects/enclave-497101/databases/(default)/documents/users/<OTHER_UID>/chats"`.
  Callable probe: call `backfillSyncFeedCallable` as non-subscriber -> `permission-denied` (`assertUserEntitled` throws it, `assertEntitled.ts:80-86`). Anonymous R2 model GET (a `.gguf`/`.onnx` object URL) -> 401/403. Config JSONs still 200: `curl -o /dev/null -w "%{http_code}" https://config.enclave-app.com/model_config.json` and `.../trusted_domains.json`.
- [ ] Subscriber in-app model download works (presigned URL path, `mintModelDownloadUrl`).
- [ ] Share flow as subscriber: create a share link, open it in a logged-out browser — page renders (public `shared_chats` read carve-out) and encrypted media loads (public `shared_chat_media` read carve-out).
- [ ] Non-subscriber share attempt: creating a share fails cleanly in-app (callable `permission-denied` mapped by `ShareFailureMapper`), no crash, no retry loop.
- [ ] Non-subscriber Storage probe: direct `https://firebasestorage.googleapis.com` read of another user's object path with a non-subscriber token -> 403.
- [ ] Purge path still works for non-subscribers: delete own cloud data in-app succeeds (delete carve-outs in both rules files) — verify the `users/{uid}` collections empty out in console.
- [ ] Moderation report path unaffected: submitting a false-positive/negative report succeeds without subscription (write-only `moderation` create carve-out).

## STEP 5 — 48h watch

- [ ] Firebase console > Firestore > Usage: request volume roughly matches pre-gate baseline minus non-subscribers.
- [ ] Expect elevated `PERMISSION_DENIED` in rules evaluation from non-subscribers probing sync — flatline of successful sync writes from non-entitled uids is the healthy signal, not zero denies.
- [ ] GCP alert notifications fire only on real spend anomalies.
- [ ] Stripe Dashboard (Live) > Payments: every successful payment within the window has a matching `users/{uid}/entitlement/stripe` doc with `active: true`. Spot-check at least three. A payment with no doc means the webhook missed an event — replay it from Developers > Webhooks > select endpoint > failed delivery > Resend.
- [ ] Firebase console > Functions > Logs: filter `stripeWebhook` for any 500s. 500 means handler failure with possible retry storms (unknown/unresolvable events correctly return 200 per `stripeWebhook.ts` header comment — only unexpected errors 500).
- [ ] Client crash-free check: no new crashes around Settings subscribe row or download screen in the sideloaded build. The ENC-241 gate surfaces `SubscriptionRequired` as UI state (`SettingsScreen.kt:710` shows "Not subscribed"), never as an exception — a crash there is a client bug, not a gate problem.
- [ ] R2 egress check in Cloudflare: anonymous (unsigned) object GETs should be ~zero after the flip; signed-URL GETs track subscriber downloads. If anonymous GETs stay high, the bucket flip missed some model paths — re-check the path rules from STEP 2.
- [ ] At 48h, decide: steady state (leave it), or tune (fix-forward thresholds, budget numbers, alert routing). Record the decision and the observed deny/write volumes in the Linear ENC-244 thread for the record.

## Quick diagnosis (symptom → cause → fix)

| Symptom | Likely cause | Fix |
|---|---|---|
| You see "Not subscribed" with override present | Wrong uid, or `freeAccess` saved as string not boolean | Re-check Auth uid; field type must be boolean `true` (`assertEntitled.ts` uses `=== true`) |
| Paid but app says "Not subscribed" | `STRIPE_PRICE_ID` mismatch, or webhook secret wrong (400s in Stripe deliveries) | Match live price id; re-set `STRIPE_WEBHOOK_SECRET`, redeploy webhook |
| Webhook deliveries show 500 | Handler threw unexpectedly (`stripeWebhook.ts` catch-all) | `firebase functions:log --only stripeWebhook`; fix forward, then Resend from Stripe |
| Subscriber sync works, downloads fail | `.env` R2 vars missing, or presigned URL expired | Check `functions/.env.enclave-497101`; client must not cache minted URLs past TTL |
| Non-subscriber sync still works | Rules not yet deployed, or deployed from wrong dir | Redeploy `--only firestore:rules` from repo root; confirm console timestamp |
| Everything 403 including config JSONs | Cloudflare flip took the JSONs private too | Re-publish the two JSON paths; verify both 200s anonymously |
| `functions:secrets:set` prompts for force | Secret already referenced by deployed functions | Answer yes / pass `--force` (verified flag in `--help`); then redeploy affected functions |

## ROLLBACK

- [ ] Rules: redeploy the saved copies — copy `~/enclave-rollback/<date>/firestore.rules.deployed.bak` over `firestore.rules` (or point `--config` at it), then `firebase deploy -P enclave-497101 --only firestore:rules`; same for `storage.rules` with `--only storage`. (No CLI fetch exists; console Firestore > Rules > history is the alternative source of the last-good text.)
- [ ] Functions: checkout `<PRE_GATE_SHA>` in a scratch worktree (`git worktree add /tmp/pregate <PRE_GATE_SHA>`), then from that worktree `firebase deploy -P enclave-497101 --only functions:uploadSharedChat,functions:backfillSyncFeedCallable,functions:rebuildProviderDailyRollups,functions:mintModelDownloadUrl` to restore pre-gate behavior. Remove the worktree after.
- [ ] R2: flip the bucket/model objects back to public in Cloudflare.
- [ ] Roll back (vs fix forward) when: your own uid is locked out with no working override write path, or subscriber checkouts stop writing entitlement docs. Fix forward when: only non-subscriber edge cases misbehave (tune rules/callables, redeploy named functions).
- [ ] If locked out of Firestore console writes too (rules deny even console? No — console uses admin credentials, rules never block you there): re-add the override doc from the console and re-test before touching deploys. Most "lockouts" are a missing override doc, not a bad deploy.
- [ ] If a named-function redeploy fails mid-enforcement: `npm --prefix functions run build` locally first, fix the type error, redeploy ONLY the failed function (`--only functions:<name>`). Never run a bare `firebase deploy` (no `--only`) during this runbook — it would also push Hosting rewrites and any unrelated local state.
- [ ] After any rollback, re-run the STEP 4 smoke boxes that cover what you rolled back, then decide whether to re-attempt enforcement the same day or stand down.

## Appendix A — Secret/env inventory

| Name | Where set | Used by |
|---|---|---|
| `STRIPE_SECRET_KEY` | `firebase functions:secrets:set` | `stripeWebhook`, `createCheckoutSession`, `createPortalSession` |
| `STRIPE_WEBHOOK_SECRET` | `firebase functions:secrets:set` | `stripeWebhook` |
| `STRIPE_PRICE_ID` | `functions/.env.enclave-497101` (plain env) | `createCheckoutSession` |
| `ENCLAVE_CHECKOUT_SUCCESS_URL` | `.env` file (optional; default subscribe URL + `?checkout=success`) | `createCheckoutSession` |
| `ENCLAVE_CHECKOUT_CANCEL_URL` | `.env` file (optional; default + `?checkout=cancel`) | `createCheckoutSession` |
| `ENCLAVE_PORTAL_RETURN_URL` | `.env` file (optional; default subscribe URL) | `createPortalSession` |
| `R2_ENDPOINT` | `functions/.env.enclave-497101` (plain env) | `mintModelDownloadUrl` |
| `R2_BUCKET` | `functions/.env.enclave-497101` (plain env) | `mintModelDownloadUrl` |
| `R2_ACCESS_KEY_ID` | `firebase functions:secrets:set` | `mintModelDownloadUrl` |
| `R2_SECRET_ACCESS_KEY` | `firebase functions:secrets:set` | `mintModelDownloadUrl` |

## Post-rollout cleanup (after 48h green)

- [ ] Delete `functions/.env.enclave-497101` from local disk. It is NOT gitignored (no matching entry in `.gitignore` — verified via `git check-ignore`, which matches nothing), so it must never be committed. Verify with `git status --porcelain` showing no `.env.enclave-497101` entry before any commit on this branch.
- [ ] Keep `~/enclave-rollback/<date>/*.bak` for one release cycle, then delete.
- [ ] Move Linear ENC-244 to done only after STEP 5's 48h decision is recorded.

## Key URLs (bookmark these before enforcement day)

- Firebase console project: `https://console.firebase.google.com/project/enclave-497101/firestore` (Data, Rules tabs)
- Functions logs: `firebase functions:log -P enclave-497101 --only <name>` (script `logs` in `functions/package.json`)
- Stripe test webhooks: Dashboard (Test mode) > Developers > Webhooks; live: same path with the mode toggle on Live
- Subscribe page (client constant `ENCLAVE_SUBSCRIBE_URL`): `https://shippopotamuslabs.com/enclave/subscribe`
- Config JSONs: `https://config.enclave-app.com/model_config.json`, `https://config.enclave-app.com/trusted_domains.json`
- Webhook endpoint: `https://us-central1-enclave-497101.cloudfunctions.net/stripeWebhook` (confirm exact host in Functions console after first deploy)

Secret rotation note: rotating any `*_SECRET` later means `functions:secrets:set <KEY>` (new value becomes latest version) followed by redeploying every function in Appendix B that lists it — secrets are versioned, and deployed functions keep the version they were deployed with until redeployed. Plain `.env` vars rotate the same way via the `.env.enclave-497101` file + redeploy.

## Appendix B — Function inventory

| Function | Trigger | Gate |
|---|---|---|
| `stripeWebhook` | HTTPS onRequest | Stripe signature; writes entitlement docs |
| `createCheckoutSession` | callable | auth only (starts checkout) |
| `createPortalSession` | callable | auth only (manages billing) |
| `mintModelDownloadUrl` | callable | `assertUserEntitled` (stripe OR override) |
| `uploadSharedChat` | callable | auth + `assertUserEntitled` |
| `backfillSyncFeedCallable` | callable | auth + `assertUserEntitled` |
| `rebuildProviderDailyRollups` | callable | auth + override-only (`assertOverrideEntitled`) |
| `cleanupExpiredShares` | pubsub schedule `every 24 hours` | none (Admin SDK) |
| `handleSyncEntityWrite(Trigger)` | Firestore trigger | none (Admin SDK, server-side feed writer) |
| `share` | HTTPS onRequest | none (public share page renderer) |

## Appendix C — Carve-outs (stay open by design)

| Area | Rule location | Behavior |
|---|---|---|
| `users/{uid}` doc read/write (owner only) | `firestore.rules` owner match | never gated — identity, not sync |
| Entitlement doc reads (owner) | `firestore.rules` `entitlement/{docId}` match | owner-read, client writes denied (server-only via Admin SDK) |
| Entity deletes (purge path) | `firestore.rules` collection match `allow delete`; `storage.rules` `users/{userId}` `allow delete` | non-subscribers can always purge own data |
| `shared_chats` reads | `firestore.rules` `allow read: if true` | public share page; writes server-only |
| `shared_chat_media` reads | `storage.rules` `allow read: if true` | public share media; entitled-owner writes |
| `moderation` reports | `firestore.rules` create-only with field allowlist | write-only, no read/update/delete |
| `stripe_customers` mapping | `firestore.rules` `allow read, write: if false` | webhook Admin SDK only |
| `sync_events` / `sync_meta` reads | `firestore.rules` entitled-owner read, `allow write: if false` | clients never write; trigger writes via Admin SDK |
| `sync_processed_events` | `firestore.rules` deny all | idempotency ledger, server-only |
| `model_config.json` + `trusted_domains.json` | R2 public paths (not Firebase rules) | must stay 200 at exact custom-domain URLs |
| HuggingFace downloads | `DownloadSource.HUGGING_FACE` branch, `DynamicModelUrlProvider.kt` | never gated — no R2 involved |
| On-device inference/downloads | client-side only | no server gate possible |

`users/{uid}` doc read/write (owner only); entitlement doc reads (owner); deletes of own sync entities and Storage objects (purge path); `shared_chats` Firestore reads (public) with writes server-only; `shared_chat_media` Storage reads (public share page) with entitled-owner writes; `moderation` create-only write-only reports; `stripe_customers` server-only; `model_config.json` + `trusted_domains.json` public at `config.enclave-app.com`; HuggingFace model downloads (no gate); all on-device inference/downloads.
