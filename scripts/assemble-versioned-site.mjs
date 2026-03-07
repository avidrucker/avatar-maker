import {mkdtemp, rm, mkdir, cp, writeFile, readdir} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join} from "node:path";
import {execFile} from "node:child_process";
import {promisify} from "node:util";

const execFileAsync = promisify(execFile);

const {
  GITHUB_TOKEN,
  GITHUB_REPOSITORY,
  GITHUB_SHA,
  GITHUB_RUN_NUMBER,
  GITHUB_WORKFLOW_FILE = "pages.yml",
  MAX_BUILDS = "30",
} = process.env;

if (!GITHUB_TOKEN || !GITHUB_REPOSITORY || !GITHUB_SHA || !GITHUB_RUN_NUMBER) {
  throw new Error("Missing required GitHub environment variables.");
}

const [owner, repo] = GITHUB_REPOSITORY.split("/");
const runNumber = Number(GITHUB_RUN_NUMBER);
const sha7 = GITHUB_SHA.slice(0, 7);
const maxBuilds = Math.max(1, Number(MAX_BUILDS) || 30);
const srcDir = "public";
const outDir = "dist-pages";
const basePath = `/${repo}/`;

function log(msg) {
  process.stdout.write(`${msg}\n`);
}

async function gh(path) {
  const res = await fetch(`https://api.github.com${path}`, {
    headers: {
      Authorization: `Bearer ${GITHUB_TOKEN}`,
      Accept: "application/vnd.github+json",
      "User-Agent": "avatar-maker-pages-builder",
      "X-GitHub-Api-Version": "2022-11-28",
    },
  });
  if (!res.ok) {
    throw new Error(`GitHub API ${path} failed: ${res.status}`);
  }
  return res.json();
}

async function copyDirContents(src, dest) {
  await mkdir(dest, {recursive: true});
  const entries = await readdir(src);
  for (const entry of entries) {
    await cp(join(src, entry), join(dest, entry), {recursive: true, force: true});
  }
}

async function copyPublicTo(dirPath) {
  await mkdir(dirPath, {recursive: true});
  await copyDirContents(srcDir, dirPath);
}

async function downloadRunArtifact(runId) {
  const artifacts = await gh(`/repos/${owner}/${repo}/actions/runs/${runId}/artifacts?per_page=100`);
  const match = (artifacts.artifacts || []).find((a) => a.name === "github-pages" && !a.expired);
  if (!match) return null;

  const zipRes = await fetch(match.archive_download_url, {
    headers: {
      Authorization: `Bearer ${GITHUB_TOKEN}`,
      Accept: "application/vnd.github+json",
      "User-Agent": "avatar-maker-pages-builder",
    },
    redirect: "follow",
  });
  if (!zipRes.ok) return null;

  const tmpBase = await mkdtemp(join(tmpdir(), "avatar-pages-"));
  const zipPath = join(tmpBase, "artifact.zip");
  const unzipDir = join(tmpBase, "unzip");
  const extractDir = join(tmpBase, "extract");
  await mkdir(unzipDir, {recursive: true});
  await mkdir(extractDir, {recursive: true});

  const zipBytes = new Uint8Array(await zipRes.arrayBuffer());
  await writeFile(zipPath, zipBytes);
  await execFileAsync("unzip", ["-q", zipPath, "-d", unzipDir]);

  const tarPath = join(unzipDir, "artifact.tar");
  try {
    await execFileAsync("tar", ["-xf", tarPath, "-C", extractDir]);
  } catch {
    return null;
  }
  return {tmpBase, extractDir};
}

async function main() {
  await rm(outDir, {recursive: true, force: true});
  await mkdir(outDir, {recursive: true});

  log("Copying latest build to root and version aliases...");
  await copyPublicTo(outDir);
  await copyPublicTo(join(outDir, String(runNumber)));
  await copyPublicTo(join(outDir, sha7));

  const runsResponse = await gh(
    `/repos/${owner}/${repo}/actions/workflows/${encodeURIComponent(GITHUB_WORKFLOW_FILE)}/runs?branch=main&status=success&per_page=${maxBuilds}`
  );
  const workflowRuns = (runsResponse.workflow_runs || [])
    .filter((r) => r.conclusion === "success")
    .sort((a, b) => b.run_number - a.run_number)
    .slice(0, maxBuilds);

  const builds = [];
  const presentTokens = new Set([String(runNumber), sha7]);

  builds.push({
    run_number: runNumber,
    sha: sha7,
    created_at: new Date().toISOString(),
    available: true,
    source: "current",
  });

  for (const run of workflowRuns) {
    const rn = Number(run.run_number);
    const rsha = String(run.head_sha || "").slice(0, 7);
    if (!rn || !rsha) continue;

    if (rn === runNumber) {
      continue;
    }

    if (!presentTokens.has(String(rn))) {
      log(`Attempting to backfill build #${rn} (${rsha}) from workflow artifact...`);
      const artifact = await downloadRunArtifact(run.id);
      if (artifact?.extractDir) {
        try {
          await copyDirContents(artifact.extractDir, join(outDir, String(rn)));
          await copyDirContents(artifact.extractDir, join(outDir, rsha));
          presentTokens.add(String(rn));
          presentTokens.add(rsha);
          log(`Backfilled build #${rn}.`);
        } catch {
          log(`Skipping build #${rn}; artifact extraction copy failed.`);
        } finally {
          await rm(artifact.tmpBase, {recursive: true, force: true});
        }
      } else {
        log(`Skipping build #${rn}; no downloadable artifact found.`);
      }
    }

    if (presentTokens.has(String(rn))) {
      builds.push({
        run_number: rn,
        sha: rsha,
        created_at: run.created_at,
        available: true,
        source: "artifact",
      });
    }
  }

  const deduped = Array.from(
    new Map(
      builds
        .filter((b) => b.available)
        .sort((a, b) => b.run_number - a.run_number)
        .map((b) => [String(b.run_number), b])
    ).values()
  );

  const manifest = {
    basePath,
    generatedAt: new Date().toISOString(),
    latest: {
      run_number: runNumber,
      sha: sha7,
    },
    builds: deduped,
  };

  await writeFile(join(outDir, "versions.json"), JSON.stringify(manifest, null, 2));

  await writeFile(
    join(outDir, "version-index.txt"),
    deduped.map((b) => `#${b.run_number}\t${b.sha}\t${b.created_at}`).join("\n") + "\n"
  );

  log(`Assembled ${deduped.length} build entries in ${outDir}/versions.json`);
}

await main();
