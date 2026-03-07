import {mkdtemp, rm, mkdir, cp, writeFile, readdir, lstat} from "node:fs/promises";
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
  PERSIST_BUILD_NUMBERS = "",
  REBUILD_MISSING_FROM_SOURCE = "false",
  MAX_REBUILDS = "0",
} = process.env;

if (!GITHUB_TOKEN || !GITHUB_REPOSITORY || !GITHUB_SHA || !GITHUB_RUN_NUMBER) {
  throw new Error("Missing required GitHub environment variables.");
}

const [owner, repo] = GITHUB_REPOSITORY.split("/");
const runNumber = Number(GITHUB_RUN_NUMBER);
const sha7 = GITHUB_SHA.slice(0, 7);
const maxBuilds = Math.max(1, Number(MAX_BUILDS) || 30);
const configuredRunNumbers = new Set(
  String(PERSIST_BUILD_NUMBERS || "")
    .split(",")
    .map((s) => s.trim())
    .filter((s) => /^\d+$/.test(s))
);
const persistAllowlistEnabled = configuredRunNumbers.size > 0;
const rebuildMissingFromSource = REBUILD_MISSING_FROM_SOURCE === "true";
const maxRebuilds = Math.max(0, Number(MAX_REBUILDS) || 0);
const srcDir = "public";
const outDir = "dist-pages";
const basePath = `/${repo}/`;

function log(msg) {
  process.stdout.write(`${msg}\n`);
}

function shouldPersistRunNumber(runNum) {
  if (!persistAllowlistEnabled) return true;
  return runNum === runNumber || configuredRunNumbers.has(String(runNum));
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

async function run(cmd, args, cwd) {
  await execFileAsync(cmd, args, {cwd});
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

async function seedFromPreviousDeployment(currentRunNumber) {
  const runsResponse = await gh(
    `/repos/${owner}/${repo}/actions/workflows/${encodeURIComponent(GITHUB_WORKFLOW_FILE)}/runs?branch=main&status=success&per_page=30`
  );
  const previousRuns = (runsResponse.workflow_runs || [])
    .filter((r) => Number(r.run_number) < currentRunNumber)
    .sort((a, b) => b.run_number - a.run_number);

  for (const run of previousRuns) {
    const artifact = await downloadRunArtifact(run.id);
    if (!artifact?.extractDir) continue;
    try {
      await copyDirContents(artifact.extractDir, outDir);
      return {seeded: true, fromRun: Number(run.run_number), tmpBase: artifact.tmpBase};
    } catch {
      await rm(artifact.tmpBase, {recursive: true, force: true});
    }
  }
  return {seeded: false, fromRun: null, tmpBase: null};
}

async function pruneVersionDirs(allowedTokens) {
  const entries = await readdir(outDir);
  for (const entry of entries) {
    const fullPath = join(outDir, entry);
    let isDir = false;
    try {
      isDir = (await lstat(fullPath)).isDirectory();
    } catch {
      isDir = false;
    }
    if (!isDir) continue;
    const looksLikeVersionDir = /^\d+$/.test(entry) || /^[0-9a-f]{7}$/i.test(entry);
    if (looksLikeVersionDir && !allowedTokens.has(entry)) {
      await rm(fullPath, {recursive: true, force: true});
    }
  }
}

async function rebuildRunFromSource(headSha) {
  const tmpBase = await mkdtemp(join(tmpdir(), "avatar-rebuild-"));
  const worktreeDir = join(tmpBase, "worktree");
  try {
    await run("git", ["fetch", "--no-tags", "--depth", "1", "origin", headSha]);
    await run("git", ["worktree", "add", "--detach", worktreeDir, headSha]);
    await run("npm", ["ci"], worktreeDir);
    await run("npm", ["run", "build"], worktreeDir);
    return {tmpBase, buildDir: join(worktreeDir, "public")};
  } catch {
    try {
      await run("git", ["worktree", "remove", "--force", worktreeDir]);
    } catch {
      // Best effort cleanup.
    }
    await rm(tmpBase, {recursive: true, force: true});
    return null;
  }
}

async function main() {
  await rm(outDir, {recursive: true, force: true});
  await mkdir(outDir, {recursive: true});

  const seed = await seedFromPreviousDeployment(runNumber);
  if (seed.seeded) {
    log(`Seeded ${outDir} from previous deployed artifact (run #${seed.fromRun}).`);
    if (seed.tmpBase) {
      await rm(seed.tmpBase, {recursive: true, force: true});
    }
  } else {
    log("No previous deployment artifact available for seed step.");
  }

  log("Copying latest build to root and version aliases...");
  await copyPublicTo(outDir);
  await copyPublicTo(join(outDir, String(runNumber)));
  await copyPublicTo(join(outDir, sha7));

  const runsResponse = await gh(
    `/repos/${owner}/${repo}/actions/workflows/${encodeURIComponent(GITHUB_WORKFLOW_FILE)}/runs?branch=main&status=success&per_page=${maxBuilds}`
  );
  const workflowRuns = (runsResponse.workflow_runs || [])
    .filter((r) => r.conclusion === "success")
    .filter((r) => shouldPersistRunNumber(Number(r.run_number)))
    .sort((a, b) => b.run_number - a.run_number)
    .slice(0, maxBuilds);

  const builds = [];
  const diagnostics = [];
  const presentTokens = new Set();
  const sourceByRun = new Map([[String(runNumber), "current"]]);
  let rebuildCount = 0;

  const outEntries = await readdir(outDir);
  for (const entry of outEntries) {
    const fullPath = join(outDir, entry);
    try {
      const st = await lstat(fullPath);
      if (!st.isDirectory()) continue;
      if (/^\d+$/.test(entry) || /^[0-9a-f]{7}$/i.test(entry)) {
        presentTokens.add(entry);
      }
    } catch {
      // Ignore unreadable entry.
    }
  }

  builds.push({
    run_number: runNumber,
    sha: sha7,
    created_at: new Date().toISOString(),
    available: true,
    source: "current",
  });
  diagnostics.push({
    run_number: runNumber,
    sha: sha7,
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
          sourceByRun.set(String(rn), "artifact");
          diagnostics.push({
            run_number: rn,
            sha: rsha,
            available: true,
            source: "artifact",
          });
          log(`Backfilled build #${rn}.`);
        } catch {
          diagnostics.push({
            run_number: rn,
            sha: rsha,
            available: false,
            reason: "artifact-copy-failed",
          });
          log(`Skipping build #${rn}; artifact extraction copy failed.`);
        } finally {
          await rm(artifact.tmpBase, {recursive: true, force: true});
        }
      } else {
        if (rebuildMissingFromSource && rebuildCount < maxRebuilds) {
          log(`No artifact for #${rn}; rebuilding from source at ${rsha}...`);
          const rebuilt = await rebuildRunFromSource(run.head_sha);
          if (rebuilt?.buildDir) {
            try {
              await copyDirContents(rebuilt.buildDir, join(outDir, String(rn)));
              await copyDirContents(rebuilt.buildDir, join(outDir, rsha));
              presentTokens.add(String(rn));
              presentTokens.add(rsha);
              sourceByRun.set(String(rn), "rebuilt");
              rebuildCount += 1;
              diagnostics.push({
                run_number: rn,
                sha: rsha,
                available: true,
                source: "rebuilt",
              });
              log(`Rebuilt and backfilled build #${rn}.`);
            } catch {
              diagnostics.push({
                run_number: rn,
                sha: rsha,
                available: false,
                reason: "rebuild-copy-failed",
              });
              log(`Skipping build #${rn}; rebuilt output copy failed.`);
            } finally {
              try {
                await run("git", ["worktree", "remove", "--force", join(rebuilt.tmpBase, "worktree")]);
              } catch {
                // Best effort cleanup.
              }
              await rm(rebuilt.tmpBase, {recursive: true, force: true});
            }
          } else {
            diagnostics.push({
              run_number: rn,
              sha: rsha,
              available: false,
              reason: "rebuild-failed",
            });
            log(`Skipping build #${rn}; source rebuild failed.`);
          }
        } else {
          diagnostics.push({
            run_number: rn,
            sha: rsha,
            available: false,
            reason: rebuildMissingFromSource ? "rebuild-limit-reached" : "artifact-unavailable",
          });
          log(`Skipping build #${rn}; no downloadable artifact found.`);
        }
      }
    }

    if (presentTokens.has(String(rn))) {
      builds.push({
        run_number: rn,
        sha: rsha,
        created_at: run.created_at,
        available: true,
        source: sourceByRun.get(String(rn)) || "artifact",
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

  const tokensToKeep = new Set([String(runNumber), sha7]);
  for (const b of deduped) {
    tokensToKeep.add(String(b.run_number));
    tokensToKeep.add(String(b.sha));
  }
  await pruneVersionDirs(tokensToKeep);

  const manifest = {
    basePath,
    generatedAt: new Date().toISOString(),
    latest: {
      run_number: runNumber,
      sha: sha7,
    },
    builds: deduped,
    diagnostics: {
      seeded_from_previous_run: seed.fromRun,
      persist_allowlist_enabled: persistAllowlistEnabled,
      persist_allowlist_runs: Array.from(configuredRunNumbers).sort((a, b) => Number(a) - Number(b)),
      total_successful_runs_seen: workflowRuns.length,
      available_count: deduped.length,
      unavailable: diagnostics
        .filter((d) => d.available === false)
        .sort((a, b) => b.run_number - a.run_number),
    },
  };

  await writeFile(join(outDir, "versions.json"), JSON.stringify(manifest, null, 2));

  await writeFile(
    join(outDir, "version-index.txt"),
    deduped.map((b) => `#${b.run_number}\t${b.sha}\t${b.created_at}`).join("\n") + "\n"
  );

  await writeFile(
    join(outDir, "version-diagnostics.txt"),
    diagnostics
      .sort((a, b) => b.run_number - a.run_number)
      .map((d) => {
        if (d.available) {
          return `#${d.run_number}\t${d.sha}\tAVAILABLE\t${d.source}`;
        }
        return `#${d.run_number}\t${d.sha}\tMISSING\t${d.reason}`;
      })
      .join("\n") + "\n"
  );

  log(`Assembled ${deduped.length} build entries in ${outDir}/versions.json`);
}

await main();
