# Mobile UI theme and branding

The shared Compose UI uses Actual Budget's semantic palette: Actual Purple for
selection and primary actions, navy-neutral surfaces, and green, red, and gold
for positive, negative, and warning states. The light theme follows Actual's
published light values. Dark mode maps the same roles onto navy surfaces and
uses a lighter purple for contrast. Pure Black remains available as the OLED
option. A saved accent choice is preserved; new installs start with Actual
Purple.

The main header uses a cloud icon to summarize sync state: green means sync is
enabled and current, blue means work is pending or in progress, red means the
server or a change needs attention, and gray with a slash means sync is off.
Tapping it opens Sync Center. Accent choices use a simple swatch/name row with
a selection mark; Background and Motion keeps a style sample and three
selectable background choices. Visual Settings has no decorative live-preview
card.

The active logo sources are `mobile/branding/tandem-mark.svg` and its light
background variant, `mobile/branding/tandem-mark-light.svg`. Shared Compose
screens choose the purple tile on dark surfaces and the purple mark on white
for light surfaces; both are clipped to the same squircle. Android uses the
purple field as its adaptive-icon background with the white mark inset as the
foreground. iOS uses the purple-field mark as its app icon and applies its
native squircle mask. The Android system splash and in-app loading mark use the
purple squircle tile on the navy splash background.

Android uses an adaptive icon and a navy system splash; the first system splash
shows an inset squircle tile without the mask cutting through the tile or mark.
The app's own loading screen follows after it. The iOS host project at
`mobile/iosApp/TandemForActual.xcodeproj` contains the app icon, launch
storyboard, and native background-task entry point. Its build phase links the
Kotlin framework through
`:composeApp:embedAndSignAppleFrameworkForXcode`.
The shared appearance preference is forwarded to SwiftUI, so iOS system chrome
follows the selected light, dark, or system mode alongside the Compose screens.

## Wording

| Idea                          | Use                  | Avoid                                         |
| ----------------------------- | -------------------- | --------------------------------------------- |
| A local copy                  | Saved on this device | Saved on this phone                           |
| Repeating an action           | Try again            | Retry sync, Retry edit, Retry loading budgets |
| Removing a pending local edit | Remove edit          | Discard or Dismiss                            |
| Deleting one transaction      | Delete transaction?  | Delete this transaction?                      |

Keep action labels consistent when the surrounding screen already makes the
object clear. Use a short next step in error messages, such as “Try again.”

Android builds and physical-device display checks run from Linux. The shared
Kotlin targets compile for iOS here, but the native Swift host, Xcode asset
catalog, and iOS launch screen need a macOS/Xcode build for verification.
