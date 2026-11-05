import { log } from "node:console";
import { createRequire } from "node:module";
import { dirname, join, resolve } from "node:path";
import process from "node:process";
import {
  copyFile,
  mkdir,
  readFile,
  readdir,
  rm,
  stat,
  writeFile,
} from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { Resvg } from "@resvg/resvg-js";

const require = createRequire(import.meta.url);
const repositoryDirectory = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "..",
);
const outputArgument = process.argv.indexOf("--output");
const outputDirectory = resolve(
  outputArgument === -1
    ? "mobile/composeApp/build/generated/thesvg-icon-pack/composeResources"
    : process.argv[outputArgument + 1],
);
const imageSizeArgument = process.argv.indexOf("--size");
const imageSize =
  imageSizeArgument === -1 ? 72 : Number(process.argv[imageSizeArgument + 1]);

if (!Number.isInteger(imageSize) || imageSize < 24 || imageSize > 512) {
  throw new Error("--size must be a whole number from 24 through 512.");
}

const packageDirectory = dirname(require.resolve("@thesvg/icons/target"));
const iconModules = (await readdir(packageDirectory)).filter(
  (file) => file.endsWith(".cjs") && file !== "index.cjs",
);
const openSourceLicenses = new Set(["Apache-2.0", "CC0-1.0", "MIT"]);
const merchantCategories = new Set([
  "Automotive",
  "Aviation",
  "Booking",
  "Communication",
  "E-commerce",
  "Education",
  "Energy",
  "Entertainment",
  "Fashion",
  "Finance",
  "Food",
  "Hardware",
  "Health",
  "Hospitality",
  "Insurance",
  "Investment",
  "Jewelry",
  "Logistics",
  "Marketplace",
  "Media",
  "Music",
  "Oil & Gas",
  "Payment",
  "Photography",
  "Real Estate",
  "Services",
  "Shopping",
  "Sports",
  "Telecommunications",
  "Transportation",
  "Travel",
]);

function permittedLicense(license) {
  if (openSourceLicenses.has(license)) return true;
  return (
    license.trim().toLowerCase() === "fair use" ||
    /^trademark of .+used for identification under nominative fair use\.?$/i.test(
      license,
    )
  );
}

function licenseFamily(license) {
  return /fair use/i.test(license) ? "fair-use" : "open-source";
}

function viewBoxRatio(svg) {
  const viewBox = svg.match(
    /\bviewBox=["']\s*([\d.+-]+)[ ,]+([\d.+-]+)[ ,]+([\d.+-]+)[ ,]+([\d.+-]+)\s*["']/i,
  );
  if (viewBox) {
    const width = Number(viewBox[3]);
    const height = Number(viewBox[4]);
    if (width > 0 && height > 0) return width / height;
  }
  return null;
}

function iconArtwork(icon) {
  const defaultSvg = icon.variants?.default ?? icon.svg;
  const defaultRatio = viewBoxRatio(defaultSvg);
  const monoSvg = icon.variants?.mono;
  const monoRatio = monoSvg ? viewBoxRatio(monoSvg) : null;
  if (
    defaultRatio != null &&
    defaultRatio > 1.6 &&
    monoRatio != null &&
    monoRatio <= 1.6
  ) {
    return monoSvg.replace(/<svg\b([^>]*)>/i, (root, attributes) =>
      /\bfill\s*=/i.test(attributes)
        ? root
        : `<svg${attributes} fill="#${icon.hex ?? "000000"}">`,
    );
  }
  return defaultSvg;
}

function resourceName(slug) {
  return `merchant_${slug
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")}`;
}

const icons = [];
const licenseCounts = new Map();
const resourceNames = new Map();
let skippedWithoutData = 0;
let skippedScope = 0;

for (const file of iconModules) {
  const exported = require(join(packageDirectory, file));
  const icon = exported.default ?? exported;
  if (!icon?.slug || !icon?.svg || !Array.isArray(icon.aliases)) {
    skippedWithoutData += 1;
    continue;
  }
  if (
    !permittedLicense(icon.license ?? "") ||
    !icon.categories?.some((category) => merchantCategories.has(category))
  ) {
    skippedScope += 1;
    continue;
  }

  const key = resourceName(icon.slug);
  const previousSlug = resourceNames.get(key);
  if (previousSlug && previousSlug !== icon.slug) {
    throw new Error(
      `Resource name collision: ${previousSlug} and ${icon.slug} both map to ${key}.`,
    );
  }
  resourceNames.set(key, icon.slug);
  licenseCounts.set(icon.license, (licenseCounts.get(icon.license) ?? 0) + 1);
  icons.push({
    slug: icon.slug,
    title: icon.title,
    aliases: icon.aliases.filter((alias) => typeof alias === "string"),
    categories: Array.isArray(icon.categories) ? icon.categories : [],
    license: icon.license,
    licenseFamily: licenseFamily(icon.license),
    url: icon.url ?? "",
    registryUrl: `https://thesvg.org/icon/${icon.slug}`,
    resourceName: key,
    svg: iconArtwork(icon),
  });
}

icons.sort((left, right) => left.slug.localeCompare(right.slug));
await rm(outputDirectory, { recursive: true, force: true });
const drawableDirectory = join(outputDirectory, "drawable");
const filesDirectory = join(outputDirectory, "files", "third_party");
await mkdir(drawableDirectory, { recursive: true });
await mkdir(filesDirectory, { recursive: true });

const records = [];
const failures = [];
let outputBytes = 0;
for (const icon of icons) {
  try {
    const rendered = new Resvg(icon.svg, {
      fitTo: { mode: "width", value: imageSize },
    })
      .render()
      .asPng();
    const outputPath = join(drawableDirectory, `${icon.resourceName}.png`);
    await writeFile(outputPath, rendered);
    outputBytes += rendered.byteLength;
    const record = { ...icon };
    delete record.svg;
    records.push(record);
  } catch (error) {
    failures.push({
      slug: icon.slug,
      reason: error instanceof Error ? error.message : String(error),
    });
  }
}

if (failures.length > Math.max(20, Math.ceil(icons.length * 0.01))) {
  throw new Error(
    `TheSVG SVG renderer failed on ${failures.length} of ${icons.length} eligible icons.`,
  );
}

const licenseSummary = [...licenseCounts.entries()].sort(([left], [right]) =>
  left.localeCompare(right),
);
const packageJson = JSON.parse(
  await readFile(join(packageDirectory, "..", "package.json"), "utf8"),
);
const packageLicense = await readFile(join(packageDirectory, "..", "LICENSE"));
await writeFile(
  join(filesDirectory, "THESVG-PACKAGE-LICENSE.txt"),
  packageLicense,
);
await copyFile(
  join(repositoryDirectory, "mobile/composeApp/third_party/Apache-2.0.txt"),
  join(filesDirectory, "Apache-2.0.txt"),
);
await writeFile(
  join(outputDirectory, "files", "merchant-icon-catalog.json"),
  `${JSON.stringify({
    source: "@thesvg/icons",
    sourceVersion: packageJson.version,
    generatedImageSize: imageSize,
    iconCount: records.length,
    licenseCounts: Object.fromEntries(licenseSummary),
    icons: records,
    renderFailures: failures,
  })}\n`,
);

const directorySize = async (directory) => {
  const entries = await readdir(directory, { withFileTypes: true });
  const sizes = await Promise.all(
    entries.map(async (entry) => {
      const entryPath = join(directory, entry.name);
      return entry.isDirectory()
        ? directorySize(entryPath)
        : (await stat(entryPath)).size;
    }),
  );
  return sizes.reduce((total, size) => total + size, 0);
};

const licenseFamilies = records.reduce((summary, icon) => {
  summary[icon.licenseFamily] = (summary[icon.licenseFamily] ?? 0) + 1;
  return summary;
}, {});

log(
  JSON.stringify(
    {
      source: `${packageJson.name}@${packageJson.version}`,
      sourcePackageLicense: packageJson.license,
      imageSize,
      discoveredModules: iconModules.length,
      includedIcons: records.length,
      skippedWithoutData,
      skippedLicenseOrCategory: skippedScope,
      renderFailures: failures,
      generatedPngBytes: outputBytes,
      generatedResourceBytes: await directorySize(outputDirectory),
      licenseFamilies,
      uniqueOpenSourceLicenses: licenseSummary.filter(
        ([license]) => !/fair use/i.test(license),
      ).length,
      nominativeFairUseIcons: records.filter((icon) =>
        /nominative fair use/i.test(icon.license),
      ).length,
      outputDirectory,
    },
    null,
    2,
  ),
);
