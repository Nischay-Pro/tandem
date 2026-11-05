# TheSVG merchant artwork notices

Tandem generates its offline merchant image resources from the pinned `@thesvg/icons` 4.0.4 and `@rei/cedar-icons` 2.6.1 packages, plus the Panera Bread wordmark recorded as public domain on Wikimedia Commons. The package code and tooling are MIT licensed; the package license texts are included in the generated app resources. `resvg` is only a build-time renderer and is not shipped as an app dependency.

The generator checks the complete TheSVG catalog and includes every entry reported as `CC0-1.0`, `MIT`, `Apache-2.0`, public domain, `Fair Use`, or nominative fair use. It no longer filters by category. Other license labels are excluded. The standalone REI Cedar merchant icon is MIT licensed. The Panera Bread wordmark's source file is stored in `third_party/merchant-icons/`; the Wikimedia Commons file page reports it as public domain. The source snapshot keeps builds deterministic and does not require a Commons request. The app's About screen has a searchable index of every bundled icon, its reported license, source page, and associated brand site when available.

The generator includes the Apache-2.0, TheSVG package MIT, and REI Cedar MIT license texts in app resources. CC0, MIT, Apache-2.0, public-domain, Fair Use, and source URL metadata remains with each icon record. These copyright licenses do not transfer trademark rights. Tandem displays a matched mark only to identify a payee, does not imply affiliation or endorsement, and should follow each brand's usage rules.

Logo matching happens on-device against transaction payee names. It uses a full title or alias at the beginning of the payee, with an optional numeric store/location suffix. It does not search arbitrary words elsewhere in a payee.

- Source package: [`@thesvg/icons`](https://www.npmjs.com/package/@thesvg/icons)
- TheSVG legal notice: <https://github.com/GLINCKER/thesvg/blob/main/LEGAL.md>
- Per-icon license policy: <https://github.com/GLINCKER/thesvg/blob/main/LICENSING.md>
- REI Cedar icons: <https://github.com/rei/rei-cedar>
- Panera Bread wordmark source: <https://commons.wikimedia.org/wiki/File:Panera_Bread_wordmark.svg>
- Pack generation: [`generate-thesvg-icon-pack.mjs`](../../scripts/generate-thesvg-icon-pack.mjs)
