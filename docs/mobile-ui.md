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

Tandem's supplied `tandem-ratio-1point2.svg` and inverted variant are the active
logo sources. The iOS app icon uses the inverted purple-field/white-mark
treatment with its native squircle mask. Android's adaptive launcher icon uses
the purple field as its background and the white mark as a separate, inset
foreground, so Android's launcher and task-switcher masks shape the tile once.
The shared Compose logo is rendered from the canonical squircle SVG during the
offline icon-resource generation step. The in-app logo and Android system
splash use the purple squircle tile on the dark app background.

Android uses an adaptive icon and a navy system splash; the first system splash
shows an inset squircle tile without the mask cutting through the tile or mark.
The app's own loading screen follows after it. The iOS host project at
`mobile/iosApp/TandemForActual.xcodeproj` contains the app icon, launch
storyboard, and native background-task entry point. Its build phase links the
Kotlin framework through
`:composeApp:embedAndSignAppleFrameworkForXcode`.
The shared appearance preference is forwarded to SwiftUI, so iOS system chrome
follows the selected light, dark, or system mode alongside the Compose screens.

Android builds and physical-device display checks run from Linux. The shared
Kotlin targets compile for iOS here, but the native Swift host, Xcode asset
catalog, and iOS launch screen need a macOS/Xcode build for verification.
