# Automatic lottery draws without Blaze

The workflow in [lottery-draw.yml](../.github/workflows/lottery-draw.yml) runs a Node.js
script directly against Firestore. It does not deploy Cloud Functions, enable billing,
or send notifications. It is **disabled until configured**.

## Schedule and data changes

The workflow checks hourly at seven minutes past the hour. GitHub schedules use UTC;
the script checks dates in `America/Edmonton`, including daylight saving changes.
Registration remains open through 23:59 on `registrationEndDate`. A lottery becomes
eligible at 00:00 the following Alberta day, and the next successful workflow draws it.
GitHub scheduling is best effort and can be delayed; this is not an exact-midnight guarantee.

For each eligible lottery, the script:

1. Reads the latest lottery in a Firestore transaction.
2. Checks that its referenced campsite exists and has the same owner.
3. Draws up to `numberOfWinners` distinct IDs from `registered`.
4. Updates only `pending` and `drawCompleted` in the same transaction.

Completed draws are preserved, even if `pending` is empty. Documents created before
the completion marker was introduced are supported. Unmarked documents with existing
pending/booked users are rejected for review. Invalid records do not prevent other
valid draws, but the run is marked unsuccessful so they can be investigated.

The script pages through expired lotteries using the existing single-field index on
`registrationEndDate`. It also reads completed expired documents to support legacy
records with no marker. Those reads count toward the project's shared Firestore quota.

## Keep it free

- Firebase can remain on Spark. Firestore reads/writes use its existing shared free
  quotas; no Firebase billing upgrade is part of this setup.
- Standard GitHub-hosted runners in public repositories are free.
- Private repository jobs consume the repository owner's included compute allowance.
  They can otherwise incur charges after that allowance is exhausted.
- This workflow uses a standard Ubuntu runner, no artifact uploads, and no Actions cache.
- A private repository owner must configure an **Actions spending budget that blocks
  paid usage**, confirm it covers this repository, and monitor the shared allowance.
  Do not enable the private-repository confirmation variable without that protection.
  If the account cannot block paid usage, leave this scheduler disabled.

The job-level gate prevents any runner starting until `LOTTERY_SCHEDULER_ENABLED`
is `true`. Private repositories additionally require `LOTTERY_PRIVATE_FREE_CONFIRMED`
to be `true`. The variables record the owner's confirmation; they do not configure
or independently enforce GitHub's account billing budget.

References: [GitHub Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions),
[GitHub budgets](https://docs.github.com/en/billing/concepts/budgets-and-alerts),
[Firebase Spark quotas](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans).

## Set up Google authentication

Someone with Google Cloud IAM administration access must configure this once.
Use the Firebase project's associated Google Cloud project, `campsite-reservation-system`.
No service-account private key is required or should be added to the repository.

These commands are intended for **Google Cloud Shell** (Bash), signed in as that
administrator. They create a dedicated service account and a workload identity
provider restricted to this repository's `main` branch. Review the constants if the
repository/default branch changes. Existing resources should be reused rather than
recreated if a teammate already configured them.

```bash
PROJECT_ID="campsite-reservation-system"
REPOSITORY="CMPUT-301-Group-Project-Mbappe-F26/Campsite-Reservation-System-Application"
POOL_ID="lottery-github"
PROVIDER_ID="github"
SERVICE_ACCOUNT="lottery-scheduler@${PROJECT_ID}.iam.gserviceaccount.com"
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"

gcloud services enable iamcredentials.googleapis.com sts.googleapis.com \
  --project="$PROJECT_ID"

gcloud iam service-accounts create lottery-scheduler \
  --display-name="Campsite lottery scheduler" --project="$PROJECT_ID"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${SERVICE_ACCOUNT}" --role="roles/datastore.user"

gcloud iam workload-identity-pools create "$POOL_ID" \
  --project="$PROJECT_ID" --location=global --display-name="Lottery GitHub Actions"

gcloud iam workload-identity-pools providers create-oidc "$PROVIDER_ID" \
  --project="$PROJECT_ID" --location=global --workload-identity-pool="$POOL_ID" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository == '${REPOSITORY}' && assertion.ref == 'refs/heads/main'"

gcloud iam service-accounts add-iam-policy-binding "$SERVICE_ACCOUNT" \
  --project="$PROJECT_ID" --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL_ID}/attribute.repository/${REPOSITORY}"

printf 'LOTTERY_WORKLOAD_IDENTITY_PROVIDER=projects/%s/locations/global/workloadIdentityPools/%s/providers/%s\n' \
  "$PROJECT_NUMBER" "$POOL_ID" "$PROVIDER_ID"
printf 'LOTTERY_SERVICE_ACCOUNT=%s\n' "$SERVICE_ACCOUNT"
```

`roles/datastore.user` permits database reads/writes through IAM, including operations
outside the Android client's Firestore security rules. The runner therefore verifies
the campsite/lottery ownership relationship itself. The service account is used only
by the restricted workflow. The SDK is `@google-cloud/firestore`, which supports
Google Application Default Credentials from workload identity federation.

For setup details, see [Google's authentication action](https://github.com/google-github-actions/auth).

## Activate and verify

1. Commit and merge the workflow and `tools/lottery-scheduler` onto `main` through the
   team's normal review process. Scheduled workflows only run from the default branch.
2. Complete the no-cost billing checks above. Leave `LOTTERY_SCHEDULER_ENABLED` unset
   until authentication is ready.
3. In repository **Settings > Secrets and variables > Actions > Variables**, add:

   | Variable | Value |
   | --- | --- |
   | `LOTTERY_WORKLOAD_IDENTITY_PROVIDER` | Provider identifier printed by the setup commands. |
   | `LOTTERY_SERVICE_ACCOUNT` | `lottery-scheduler@campsite-reservation-system.iam.gserviceaccount.com` |
   | `LOTTERY_PRIVATE_FREE_CONFIRMED` | `true` only for a private repo after the owner confirms paid usage is blocked. |

4. Set `LOTTERY_SCHEDULER_ENABLED` to `true`. The hourly schedule is now enabled, so
   use a test lottery and choose an activation time before the next `:07` tick if
   you want to inspect a dry run first.
5. In **Actions > Draw expired campsite lotteries > Run workflow**, use branch `main`
   and leave **dry_run** checked. The runner validates/reads but does not save draws.
6. Inspect the summary. Then manually run with **dry_run** unchecked, or wait for the
   next hourly schedule. Verify `pending` and `drawCompleted` in Firestore.

To pause automatic draws, set `LOTTERY_SCHEDULER_ENABLED` to `false` or disable the
workflow. This leaves all saved lottery results unchanged. GitHub may disable
scheduled workflows in inactive public repositories; check the Actions page if runs stop.

## Development and tests

From PowerShell in the repository root:

```powershell
npm.cmd --prefix tools/lottery-scheduler ci --ignore-scripts --no-audit --no-fund
npm.cmd --prefix tools/lottery-scheduler test
```

Tests cover sampling, deadlines, DST, retries, ownership checks, dry runs, and paging.
They use a local in-memory adapter for transaction/query behavior; they do not test
live IAM, GitHub execution, or a real Firestore transaction conflict.

`src/draw.js` mirrors the Kotlin model's sampling behavior and deadline convention.
Keep both implementations and their tests aligned when changing the draw rules.
Do not run `npm run draw` locally without intentionally configuring credentials and
`GOOGLE_CLOUD_PROJECT`; the command writes results to that project.
