# TheSVG merchant artwork notices

Tandem generates its offline merchant image resources from the pinned `@thesvg/icons` 4.0.4 package. The package's code and tooling are MIT licensed; the package license text is included in the generated app resources. `resvg` is only a build-time renderer and is not shipped as an app dependency.

The pack includes artwork only when TheSVG metadata reports one of these licenses: `CC0-1.0`, `MIT`, or `Apache-2.0`, or marks the item `Fair Use` / nominative fair use. Other license labels are excluded. The generator also limits the catalog to merchant-related categories, including shopping, food, finance, energy, transportation, health, and travel. The app's About screen has a searchable index of every bundled icon, its reported license, TheSVG registry page, and the associated brand site when available.

The generator includes the Apache-2.0 license text and the TheSVG package MIT license text in app resources. CC0, MIT, Apache-2.0, Fair Use, and source URL metadata remains with each icon record. The package does not claim to grant trademark rights. TheSVG states that brand artwork is supplied for identification and development under nominative fair use; the marks remain their owners' property. Tandem displays a matched logo only to identify a payee, does not imply affiliation or endorsement, and should follow each brand's usage rules.

Logo matching happens on-device against transaction payee names. It uses a full title or alias at the beginning of the payee, with an optional numeric store/location suffix. It does not search arbitrary words elsewhere in a payee.

- Source package: [`@thesvg/icons`](https://www.npmjs.com/package/@thesvg/icons)
- TheSVG legal notice: <https://github.com/GLINCKER/thesvg/blob/main/LEGAL.md>
- Per-icon license policy: <https://github.com/GLINCKER/thesvg/blob/main/LICENSING.md>
- Pack generation: [`generate-thesvg-icon-pack.mjs`](../../scripts/generate-thesvg-icon-pack.mjs)
