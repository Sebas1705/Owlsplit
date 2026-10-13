#!/usr/bin/env node
// Google Play phone screenshots (fastlane/metadata/android/<locale>/images/phoneScreenshots) from the
// screens the screenshot tests record with sample data: each one framed on the brand colour with a
// caption, at 1080×1920 (Play wants at most 2:1). Run it after recording them:
//
//   ./gradlew :feature:home:recordRoborazziDebug :feature:groups:recordRoborazziDebug :feature:expense:recordRoborazziDebug
//   node scripts/play-screenshots.mjs      (needs Edge or Chrome)
//
// The Play listing workflow publishes them when they change on main.
import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const { light } = JSON.parse(readFileSync(join(root, "brand/tokens.json"), "utf8")).colors;

// Screen per locale (the test's PNG) and its caption.
const shots = [
  { es: "feature/home/screenshots/home_groups.png", en: "feature/home/screenshots/home_groups_en.png",
    caption: { es: "Todos tus grupos y tu saldo, de un vistazo", en: "All your groups and your balance at a glance" } },
  { es: "feature/expense/screenshots/expense_equal.png", en: "feature/expense/screenshots/expense_equal_en.png",
    caption: { es: "Apunta un gasto en segundos", en: "Add an expense in seconds" } },
  { es: "feature/groups/screenshots/group_expenses.png", en: "feature/groups/screenshots/group_expenses_en.png",
    caption: { es: "Cada gasto, con lo que te toca", en: "Every expense, with your share" } },
  { es: "feature/groups/screenshots/group_settle.png", en: "feature/groups/screenshots/group_settle_en.png",
    caption: { es: "El mínimo de pagos para quedar en paz", en: "The fewest payments to settle up" } },
];
const locales = { es: "es-ES", en: "en-US" };

const browser = [
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  "/usr/bin/google-chrome",
  "/usr/bin/chromium",
].find(existsSync);
if (!browser) throw new Error("No Edge or Chrome found to render the screenshots");

const W = 1080, H = 1920;
for (const [lang, locale] of Object.entries(locales)) {
  const dir = join(root, `fastlane/metadata/android/${locale}/images/phoneScreenshots`);
  rmSync(dir, { recursive: true, force: true });
  mkdirSync(dir, { recursive: true });
  shots.forEach((shot, i) => {
    const image = "file:///" + join(root, shot[lang]).replaceAll("\\", "/");
    const page = join(tmpdir(), "owlsplit-shot.html");
    writeFileSync(page, `<!doctype html><html><head>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,800&display=block">
<style>body{margin:0;width:${W}px;height:${H}px;overflow:hidden;background:${light.accent};display:flex;flex-direction:column;align-items:center}
h1{margin:96px 72px 64px;color:#fff;text-align:center;text-wrap:balance;font:800 68px/1.1 "Bricolage Grotesque",sans-serif;letter-spacing:-0.02em}
img{width:760px;border-radius:44px;box-shadow:0 30px 80px rgba(0,0,0,.28);background:${light.background}}</style>
</head><body><h1>${shot.caption[lang]}</h1><img src="${image}"></body></html>`);
    const out = join(dir, `${i + 1}.png`);
    execFileSync(browser, ["--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
      "--allow-file-access-from-files", "--virtual-time-budget=5000", `--window-size=${W},${H}`, `--screenshot=${out}`,
      "file:///" + page.replaceAll("\\", "/")], { stdio: "ignore", timeout: 60_000 });
    rmSync(page);
    console.log("wrote", out.slice(root.length + 1));
  });
}
