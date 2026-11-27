# Store screenshots

For maintainers preparing the Google Play and App Store listings. The
**App screenshots** workflow captures every set from a seeded demo budget.

Tandem is an unofficial client for Actual Budget. It is not made or endorsed by
the Actual Budget project. Say so in every listing description.

## Capture

1. **Run the workflow** from the Actions tab: **App screenshots**, then **Run
   workflow**. Choose `android`, `ios`, or `both`.
2. **Download the artifacts** from the finished run. Each Android set is a
   separate artifact named `android-screenshots-<set>`, and iOS is
   `ios-screenshots`.

**Check:** the run summary of each Android job lists every screenshot, its
captured size, and the listing folder it went to. A note that it was
`(cropped)` means the emulator profile was not an exact Play ratio. Check that
the crop did not cut off content before uploading it.

## Android sets

Each job runs a real emulator profile, so foldables report a real
`FoldingFeature` and fold between the inner and outer displays. Every set has
light and dark captures of Budget, Activity, Accounts, Reports, and a category.

| Set            | Emulator profile                                 | Listing folder in the artifact                |
| -------------- | ------------------------------------------------ | --------------------------------------------- |
| `phone`        | `pixel_7`                                        | `play/phone/`                                 |
| `foldable-1-1` | `foldable_1_1`, or `pixel_9_pro_fold` without it | `play/foldable/` (1:1 inner), `play/phone/`   |
| `foldable-4-3` | `foldable_4_3`, or `pixel_fold` without it       | `play/foldable/` (4:3 inner, 10:16 outer)     |
| `tablet`       | `medium_tablet`, or `pixel_tablet` without it    | `play/tablet-7-inch/`, `play/tablet-10-inch/` |

The unsorted captures are in `raw/`. Upload the `play/` folders to the matching
Play Console sections:

- **Phone:** two to eight screenshots.
- **Foldable:** at least one 1:1 inner, one 4:3 landscape inner, and one 10:16
  portrait outer screenshot.
- **7-inch and 10-inch tablet:** the same landscape tablet screenshots in both.

## iOS sets

The `ios` job captures iPhone 17, iPhone Duo, and iPad Pro 13 on simulators.
Upload each to the matching device section in App Store Connect.

## README images

The images in `docs/images/screenshots/` come from these captures. Replace them
only when the app's look changes.

1. **Run the workflow** with `both`.
2. **Download every artifact** into an empty folder:
   `gh run download <run id> -D <folder>`.
3. **Build the images:**
   `uv run scripts/screenshots/readme-images.py <folder>`.

The script needs [Pillow](https://python-pillow.org) for WebP, which `uv`
installs for it. Without `uv`, install Pillow and run it with `python3`. It
writes light and dark versions of each image:

- **Hero:** iPhone Budget, Android Activity, iPhone Reports, and Android
  category, side by side.
- **Foldable:** Activity on the unfolded `foldable-1-1` inner display.
- **iPad:** Accounts.
- **Category:** the iPhone category screen.

**Check:** open every image. Each should show the demo budget in the right
light or dark mode. If a screen changed, update its README alt text.
