# Mobile UI theme and branding

The shared Compose UI uses Actual Budget's semantic palette: Actual Purple for
selection and primary actions, navy-neutral surfaces, and green, red, and gold
for positive, negative, and warning states. The light theme follows Actual's
published light values. Dark mode maps the same roles onto navy surfaces and
uses a lighter purple for contrast. Pure Black remains available as the OLED
option. A saved accent choice is preserved; new installs start with Actual
Purple.

Tandem's supplied logo remains its own brand mark. The app icon uses the
purple-field/white-mark treatment in `mobile/branding/tandem-app-icon.svg` and
the platform launch screens use a white transparent mark on an Actual navy
background. The supplied `tandem-purple-transparent.svg` is retained unchanged
as the purple-on-transparent source. `tandem-white-transparent.svg` is the
white-on-transparent derivative used on dark launch surfaces. The in-app mark
chooses purple on light surfaces and white on dark surfaces.

Android resources use an adaptive icon and a navy system splash. The iOS host
project at `mobile/iosApp/TandemForActual.xcodeproj` contains the app icon,
launch storyboard, and native background-task entry point. Its build phase links
the Kotlin framework through
`:composeApp:embedAndSignAppleFrameworkForXcode`.

Android builds and Pixel 10a display checks run from Linux. The shared Kotlin
targets compile for iOS here, but the native Swift host, Xcode asset catalog,
and iOS launch screen need a macOS/Xcode build for verification.
