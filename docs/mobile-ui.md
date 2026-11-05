# Mobile UI theme and branding

The shared Compose UI uses Actual Budget's semantic palette: Actual Purple for
selection and primary actions, navy-neutral surfaces, and green, red, and gold
for positive, negative, and warning states. The light theme follows Actual's
published light values. Dark mode maps the same roles onto navy surfaces and
uses a lighter purple for contrast. Pure Black remains available as the OLED
option. A saved accent choice is preserved; new installs start with Actual
Purple.

Tandem's supplied `tandem-ratio-1point2.svg` and inverted variant are the active
logo sources. The iOS app icon uses the inverted purple-field/white-mark
treatment with its native squircle mask. Android keeps a navy adaptive-icon
background and places the white mark on an inset purple squircle, leaving room
for Android's circular launcher, task-switcher, and splash masks. The in-app
mark and launch screens use a purple squircle tile with the white logo. The
transparent purple and white vectors are retained for use on other backgrounds.

Android resources use an inset adaptive icon and a navy system splash; its
first system splash shows the squircle tile without the mask cutting through
the tile or mark. The app's own loading screen follows after it. The iOS host
project at `mobile/iosApp/TandemForActual.xcodeproj` contains the app icon,
launch storyboard, and native background-task entry point. Its build phase links
the Kotlin framework through
`:composeApp:embedAndSignAppleFrameworkForXcode`.
The shared appearance preference is forwarded to SwiftUI, so iOS system chrome
follows the selected light, dark, or system mode alongside the Compose screens.

Android builds and physical-device display checks run from Linux. The shared Kotlin
targets compile for iOS here, but the native Swift host, Xcode asset catalog,
and iOS launch screen need a macOS/Xcode build for verification.
