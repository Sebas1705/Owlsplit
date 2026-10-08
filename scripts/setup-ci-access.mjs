#!/usr/bin/env node
// One-off (P11b): gives GitHub Actions what the release and deploy workflows need.
//  1. Service account owlsplit-ci@owlsplit with the least roles they use: App Distribution
//     (upload releases), Rules + Hosting + Firestore indexes (deploy from main), Service Usage
//     Consumer (required to call those APIs).
//  2. A key for it in Doppler owlsplit/prd as FIREBASE_SERVICE_ACCOUNT_JSON.
//  3. A read-only Doppler service token of owlsplit/prd in the GitHub secret DOPPLER_TOKEN.
//  4. The App Distribution tester group "testers" ([email...] arguments are added to it).
//
//   node scripts/setup-ci-access.mjs [tester-email ...]
//
// Uses the account of `npx firebase-tools login` (no gcloud needed), plus doppler and gh, all
// logged in. Run it again only to rotate the key: it keeps the account and adds a new key.
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync, readdirSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { createRequire } from "node:module";

const PROJECT = "owlsplit";
const ACCOUNT_ID = "owlsplit-ci";
const EMAIL = `${ACCOUNT_ID}@${PROJECT}.iam.gserviceaccount.com`;
const ROLES = [
  "roles/firebaseappdistro.admin",
  "roles/firebaserules.admin",
  "roles/firebasehosting.admin",
  "roles/datastore.indexAdmin",
  "roles/serviceusage.serviceUsageConsumer",
];
const APIS = [
  "iam.googleapis.com",
  "cloudresourcemanager.googleapis.com",
  "firebaseappdistribution.googleapis.com",
  "firebaserules.googleapis.com",
  "firebasehosting.googleapis.com",
];
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

const require = createRequire(import.meta.url);
const ft = firebaseTools();
const auth = require(join(ft, "lib/auth"));
const { requireAuth } = require(join(ft, "lib/requireAuth"));
const { ensure } = require(join(ft, "lib/ensureApiEnabled"));
const { Client } = require(join(ft, "lib/apiv2"));

const account = auth.getGlobalDefaultAccount();
if (!account) throw new Error("Run `npx firebase-tools login` first");
await requireAuth({ project: PROJECT, user: account.user, tokens: account.tokens });
for (const api of APIS) await ensure(PROJECT, api, "owlsplit-ci", false);

const iam = new Client({ urlPrefix: "https://iam.googleapis.com", apiVersion: "v1" });
const crm = new Client({ urlPrefix: "https://cloudresourcemanager.googleapis.com", apiVersion: "v1" });

// 1. The account (kept if it exists) and its roles.
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
const { body: policy } = await crm.post(`projects/${PROJECT}:getIamPolicy`, {});
const member = `serviceAccount:${EMAIL}`;
for (const role of ROLES) {
  const binding = policy.bindings.find((b) => b.role === role);
  if (!binding) policy.bindings.push({ role, members: [member] });
  else if (!binding.members.includes(member)) binding.members.push(member);
}
await crm.post(`projects/${PROJECT}:setIamPolicy`, { policy });
console.log(`Roles: ${ROLES.join(", ")}`);

// 2. A key, straight into Doppler (never written to disk).
const { body: key } = await iam.post(`projects/${PROJECT}/serviceAccounts/${EMAIL}/keys`, {});
const keyJson = Buffer.from(key.privateKeyData, "base64").toString("utf8");
run("doppler", ["secrets", "set", "FIREBASE_SERVICE_ACCOUNT_JSON", "--project", "owlsplit", "--config", "prd", "--silent"], keyJson);
console.log("Stored the key in Doppler owlsplit/prd as FIREBASE_SERVICE_ACCOUNT_JSON");

// 3. Read-only Doppler token for GitHub Actions.
const token = run("doppler", ["configs", "tokens", "create", "github-actions", "--project", "owlsplit", "--config", "prd", "--plain"]);
run("gh", ["secret", "set", "DOPPLER_TOKEN", "--repo", REPO], token);
console.log(`Set the GitHub secret DOPPLER_TOKEN of ${REPO}`);

// 4. Testers.
try {
  run("npx", ["--yes", "firebase-tools", "appdistribution:group:create", "Testers", "testers", "--project", PROJECT]);
} catch (e) {
  if (!String(e.message).includes("already exists")) throw e;
}
const emails = process.argv.slice(2);
if (emails.length > 0) {
  run("npx", ["--yes", "firebase-tools", "appdistribution:testers:add", ...emails, "--group-alias", "testers", "--project", PROJECT]);
  console.log(`Testers added: ${emails.join(", ")}`);
}
console.log("Done.");
