# iOS host app

Open `TandemForActual.xcodeproj` and select the shared `TandemForActual` scheme.
The Xcode build phase invokes the mobile Gradle wrapper to build and link the
`ComposeApp` framework.

To build a simulator app from the `mobile/` directory on macOS:

```sh
xcodebuild \
  -project iosApp/TandemForActual.xcodeproj \
  -scheme TandemForActual \
  -destination 'generic/platform=iOS Simulator' \
  build
```

Running on a physical iPhone requires an Apple development team selected in
Xcode. Background refresh uses the identifier declared in `Info.plist`; the app
registers it at launch and schedules it only when the user enables background
sync and has selected a budget. iOS treats the six-hour date as an earliest
start and chooses whether and when to run the refresh.

The Swift host and Xcode project have not been built in this Linux workspace.
Run the Xcode build and scheduled-task check on macOS before treating iOS
background execution as runtime-verified.
