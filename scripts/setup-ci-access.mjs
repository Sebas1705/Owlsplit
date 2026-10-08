#!/usr/bin/env node
// One-off (P11b): gives GitHub Actions what the release, staging and deploy workflows need.
//  1. Service account owlsplit-ci@owlsplit with the least roles they use: on production App
//     Distribution (releases), Rules + Hosting + Firestore indexes (deploy from main) and Service
//     Usage Consumer (to call those APIs); on staging only App Distribution.
//  2. A key for it in Doppler owlsplit/prd and owlsplit/stg as FIREBASE_SERVICE_ACCOUNT_JSON.
//  3. Read-only Doppler service tokens in the GitHub secrets DOPPLER_TOKEN (prd) and
//     DOPPLER_TOKEN_STG (stg).
//  4. The App Distribution group "testers" in both projects ([email...] arguments are added).
//
//   node scripts/setup-ci-access.mjs [tester-email ...]
//
// Uses the account of `npx firebase-tools login` (no gcloud needed), plus doppler and gh, all
// logged in. Running it again keeps the account and roles and adds a new key (rotation).
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync, readdirSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { createRequire } from "node:module";

const PROJECT = "owlsplit";
const STAGING = "owlsplit-stg";
const ACCOUNT_ID = "owlsplit-ci";
const EMAIL = `${ACCOUNT_ID}@${PROJECT}.iam.gserviceaccount.com`;
const ROLES = {
  [PROJECT]: [
    "roles/firebaseappdistro.admin",
    "roles/firebaserules.admin",
    "roles/firebasehosting.admin",
    "roles/datastore.indexAdmin",
    "roles/serviceusage.serviceUsageConsumer",
  ],
  [STAGING]: ["roles/firebaseappdistro.admin", "roles/serviceusage.serviceUsageConsumer"],
};
const APIS = {
  [PROJECT]: [
    "iam.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "firebaseappdistribution.googleapis.com",
    "firebaserules.googleapis.com",
    "firebasehosting.googleapis.com",
  ],
  [STAGING]: ["cloudresourcemanager.googleapis.com", "firebaseappdistribution.googleapis.com"],
};
const DOPPLER = { [PROJECT]: "prd", [STAGING]: "stg" };
const GITHUB_SECRET = { [PROJECT]: "DOPPLER_TOKEN", [STAGING]: "DOPPLER_TOKEN_STG" };
const REPO = "Sebas1705/Owlsplit";

/** firebase-tools as a library: the copy npx keeps in its cache (or FIREBASE_TOOLS_DIR). */
function firebaseTools() {
  if (process.env.FIREBASE_TOOLS_DIR) return process.env.FIREBASE_TOOLS_DIR;
  execFileSync("npx", ["--yes", "firebase-tools", "--version"], { stdio: "ignore", shell: true });
  const cache = join(process.env.LOCALAPPDATA ?? join(homedir(), ".npm"), "npm-cache", "_npx");
  const roots = [cache, join(homedir(), ".npm", "_npx")].filter(existsSync);
  for (const root of roots) {
    for (const dir of readdirSync(root)) {
      const candidate = join(root, dir, "node_modules", "firebase-tools");
      if (existsSync(join(candidate, "package.json"))) return candidate;
    }
  }
  throw new Error("firebase-tools not found; set FIREBASE_TOOLS_DIR");
}

function run(cmd, args, input) {
  const result = spawnSync(cmd, args, { input, encoding: "utf8", shell: true });
  if (result.status !== 0) throw new Error(`${cmd} ${args[0]} failed: ${result.stderr}`);
  return result.stdout.trim();
}

/** firebase-tools through npx; on Windows Node may abort while exiting after it already succeeded. */
function firebase(args) {
  const result = spawnSync("npx", ["--yes", "firebase-tools", ...args], { encoding: "utf8", shell: true });
  return `${result.stdout}\n${result.stderr}`;
}

const require = createRequire(import.meta.url);
const ft = firebaseTools();
const auth = require(join(ft, "lib/auth"));
const { requireAuth } = require(join(ft, "lib/requireAuth"));
const { ensure } = require(join(ft, "lib/ensureApiEnabled"));
const { Client } = require(join(ft, "lib/apiv2"));

const account = auth.getGlobalDefaultAccount();
if (!account) throw new Error("Run `npx firebase-tools login` first");
await requireAuth({ project: PROJECT, user: account.user, tokens: account.tokens });
for (const project of [PROJECT, STAGING]) {
  for (const api of APIS[project]) await ensure(project, api, "owlsplit-ci", false);
}

const iam = new Client({ urlPrefix: "https://iam.googleapis.com", apiVersion: "v1" });
const crm = new Client({ urlPrefix: "https://cloudresourcemanager.googleapis.com", apiVersion: "v1" });

// 1. The account (kept if it exists) and its roles in each project.
try {
  await iam.post(`projects/${PROJECT}/serviceAccounts`, {
    accountId: ACCOUNT_ID,
    serviceAccount: { displayName: "Owlsplit CI (releases and deploys)" },
  });
  console.log(`Created ${EMAIL}`);
} catch (e) {
  if (e.status !== 409) throw e;
  console.log(`${EMAIL} already exists`);
}
const member = `serviceAccount:${EMAIL}`;
for (const [project, roles] of Object.entries(ROLES)) {
  const { body: policy } = await crm.post(`projects/${project}:getIamPolicy`, {});
  policy.bindings ??= [];
  for (const role of roles) {
    const binding = policy.bindings.find((b) => b.role === role);
    if (!binding) policy.bindings.push({ role, members: [member] });
    else if (!binding.members.includes(member)) binding.members.push(member);
  }
  await crm.post(`projects/${project}:setIamPolicy`, { policy });
  console.log(`${project}: ${roles.join(", ")}`);
}

// 2. A key, straight into Doppler (never written to disk).
const { body: key } = await iam.post(`projects/${PROJECT}/serviceAccounts/${EMAIL}/keys`, {});
const keyJson = Buffer.from(key.privateKeyData, "base64").toString("utf8");
for (const config of Object.values(DOPPLER)) {
  run("doppler", ["secrets", "set", "FIREBASE_SERVICE_ACCOUNT_JSON", "--project", "owlsplit", "--config", config, "--silent"], keyJson);
  console.log(`Stored the key in Doppler owlsplit/${config} as FIREBASE_SERVICE_ACCOUNT_JSON`);
}

// 3. Read-only Doppler tokens for GitHub Actions.
for (const project of [PROJECT, STAGING]) {
  const config = DOPPLER[project];
  const token = run("doppler", ["configs", "tokens", "create", `github-actions-${Date.now()}`, "--project", "owlsplit", "--config", config, "--plain"]);
  run("gh", ["secret", "set", GITHUB_SECRET[project], "--repo", REPO], token);
  console.log(`Set the GitHub secret ${GITHUB_SECRET[project]} (Doppler owlsplit/${config}, read-only)`);
}

// 4. Testers.
const emails = process.argv.slice(2);
for (const project of [PROJECT, STAGING]) {
  const created = firebase(["appdistribution:group:create", "Testers", "testers", "--project", project]);
  if (!/created|already exists/i.test(created)) console.warn(`${project}: could not confirm the testers group:\n${created}`);
  if (emails.length > 0) {
    firebase(["appdistribution:testers:add", ...emails, "--group-alias", "testers", "--project", project]);
    console.log(`${project}: testers ${emails.join(", ")}`);
  }
}
console.log("Done.");
