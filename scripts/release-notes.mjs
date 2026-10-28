#!/usr/bin/env node
// Release notes for testers and stores from the GitHub Release body that `gh release create
// --generate-notes` wrote ("* <PR title> by @user in <url>").
//
//   node scripts/release-notes.mjs <version> <body-file> <out-dir>    (release.yml runs it)
//   node scripts/release-notes.mjs --check                            (CI runs it)
//
// Writes <out-dir>/en-US.txt and <out-dir>/es-ES.txt (Google Play allows 500 characters per
// language). Only user-facing changes are listed: feat/fix/perf PRs, with the conventional prefix
// removed; docs/ci/chore/build/test/refactor/style ones are left out. PR titles are in English, so
// both languages list them under a translated heading.
//
// Hand-written notes win: when fastlane/release-notes/<version>/<locale>.txt exists (e.g. for
// 1.0.0, whose PRs since the last pre-release are not what users should read), that text is used
// as is for that language. `--check` verifies every hand-written note and the Play store listing
// (fastlane/metadata/android) against Google Play's limits, so a too-long text fails the PR and
// not the release.
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const CURATED_DIR = join(ROOT, "fastlane", "release-notes");
const LISTING_DIR = join(ROOT, "fastlane", "metadata", "android");
const LOCALES = ["en-US", "es-ES"];
const PLAY_LIMIT = 500;
// Google Play limits for the store listing texts.
const LISTING_LIMITS = { "title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000 };

const read = (file) => readFileSync(file, "utf8").replace(/\r\n/g, "\n").trim();

if (process.argv[2] === "--check") {
  process.exit(check());
}

const [version, bodyFile, outDir] = process.argv.slice(2);
if (!version || !bodyFile || !outDir) {
  console.error("usage: release-notes.mjs <version> <body-file> <out-dir> | --check");
  process.exit(2);
}

const HIDDEN = /^(docs|ci|chore|build|test|refactor|style)(\(.*?\))?!?:/i;
const PREFIX = /^[a-z]+(\(.*?\))?!?:\s*/i;

const titles = readFileSync(bodyFile, "utf8")
  .split("\n")
  .map((line) => /^\*\s+(.*?)\s+by\s+@\S+\s+in\s+\S+\s*$/.exec(line)?.[1])
  .filter(Boolean)
  .filter((title) => !HIDDEN.test(title))
  .map((title) => title.replace(PREFIX, ""))
  .map((title) => title.charAt(0).toUpperCase() + title.slice(1));

/** Heading plus as many bullets as fit in Play's limit; a fallback line when nothing is user-facing. */
function notes(heading, fallback) {
  if (titles.length === 0) return `${heading}\n${fallback}`;
  let text = heading;
  for (const title of titles) {
    const next = `${text}\n• ${title}`;
    if (next.length > PLAY_LIMIT) break;
    text = next;
  }
  return text;
}

/** The hand-written notes of this version and language, or null. Too long is an error, not a cut. */
function curated(locale) {
  const file = join(CURATED_DIR, version, `${locale}.txt`);
  if (!existsSync(file)) return null;
  const text = read(file);
  if (text.length > PLAY_LIMIT) {
    console.error(`${file}: ${text.length} characters, Google Play allows ${PLAY_LIMIT}`);
    process.exit(1);
  }
  return text;
}

const generated = {
  "en-US": () => notes(`What's new in ${version}:`, "• Improvements and fixes."),
  "es-ES": () => notes(`Novedades de la ${version}:`, "• Mejoras y correcciones."),
};

mkdirSync(outDir, { recursive: true });
for (const locale of LOCALES) {
  writeFileSync(join(outDir, `${locale}.txt`), curated(locale) ?? generated[locale]());
}
console.log(readFileSync(join(outDir, "en-US.txt"), "utf8"));

/** Every hand-written note and listing text within Play's limits; 0 when all are. */
function check() {
  const problems = [];
  const report = (file, length, limit) => {
    const rel = file.slice(ROOT.length + 1).replace(/\\/g, "/");
    console.log(`${length > limit ? "TOO LONG" : "ok"}  ${rel}: ${length}/${limit}`);
    if (length > limit) problems.push(rel);
  };
  if (existsSync(CURATED_DIR)) {
    for (const ver of readdirSync(CURATED_DIR)) {
      for (const locale of LOCALES) {
        const file = join(CURATED_DIR, ver, `${locale}.txt`);
        if (!existsSync(file)) {
          console.log(`MISSING  ${file.slice(ROOT.length + 1)}`);
          problems.push(file);
          continue;
        }
        report(file, read(file).length, PLAY_LIMIT);
      }
    }
  }
  for (const locale of readdirSync(LISTING_DIR)) {
    for (const [name, limit] of Object.entries(LISTING_LIMITS)) {
      const file = join(LISTING_DIR, locale, name);
      if (existsSync(file)) report(file, read(file).length, limit);
    }
    const changelogs = join(LISTING_DIR, locale, "changelogs");
    if (existsSync(changelogs)) {
      for (const name of readdirSync(changelogs)) report(join(changelogs, name), read(join(changelogs, name)).length, PLAY_LIMIT);
    }
  }
  if (problems.length) console.error(`${problems.length} text(s) over Google Play's limits or missing`);
  return problems.length ? 1 : 0;
}
