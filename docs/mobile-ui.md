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

The first-run welcome screen gives three short benefits and shows the four
required setup steps: Server, Sign in, Budget, and First sync. After a
successful first sync, Tandem offers one optional “Stay in the loop” screen.
The notification permission request happens only after “Allow notifications”
is tapped; “Not now” continues directly to the budget. Tandem does not repeat
the onboarding prompt after either choice. If the system permission was already
denied, the platform action opens notification settings.

| Idea                                      | Use                                                                                      | Avoid                                         |
| ----------------------------------------- | ---------------------------------------------------------------------------------------- | --------------------------------------------- |
| A local copy                              | Saved on this device                                                                     | Saved on this phone                           |
| Repeating an action                       | Try again                                                                                | Retry sync, Retry edit, Retry loading budgets |
| Removing a pending local edit             | Remove edit                                                                              | Discard or Dismiss                            |
| Deleting one transaction                  | Delete transaction?                                                                      | Delete this transaction?                      |
| A conflict that needs a choice            | Needs review                                                                             | Needs your review, Review changes             |
| Allowing background sync on cellular data | Sync on mobile data                                                                      | Use unrestricted networks                     |
| Alerts for rejected edits                 | Rejected edit alerts                                                                     | Not applied alerts                            |
| OS notification permission control        | Allow notifications; Turn on in notification settings; Turn off in notification settings | System permissions, Enable, Disable           |
| Budget layout that puts essentials first  | Essentials                                                                               | Essentials first                              |
| Product description                       | A local-first app for Actual Budget                                                      |                                               |
| Pending local edit collection             | Pending Changes (screen title); pending edits in prose                                   | Pending changes in the screen title           |

Keep action labels consistent when the surrounding screen already makes the
object clear. Use a short next step in error messages, such as “Try again.”

### Messages about an edit or a sync

These rules apply to every message that reports what happened to a person’s
edit. They follow the Apple Human Interface Guidelines for writing.

- Say what happened to the edit first, then what to do. For example: “Your edit
  is still on this device. Try again.” If nothing is needed, say so.
- Every message that reports a failure ends with a next step. “Nothing was
  changed.” alone is not enough.
- Write whole sentences with a subject. Name the thing you mean instead of
  “it” or “here”: “Tandem removed the waiting deletion”, not “Cleared the
  pending deletion here.”
- Use the words people see on screen. Do not put internal terms in a message,
  such as “split child”, “revision”, “operation”, or “projection”.
- Name the states the same way on the badge, the row, the banner, and in
  messages: Needs review, Status unknown, and Rejected. Do not mix in
  “conflict” or “uncertain” as a status name. “Conflict” may still name the two
  differing versions on the review screen.
- For an amount that is refused, say which mark to use and give an example
  that fits the budget. Say “decimals” rather than “cents” unless the currency
  has exactly two decimal places.
- Use curly apostrophes in text. The apostrophe check in CI enforces this.
- Keep an alert switch for a phone notification separate from the in-app
  banner. A banner for a change that needs review, a rejected edit, or an edit
  with an unknown status always shows, whatever the notification switches say.

Android builds and physical-device display checks run from Linux. The shared
Kotlin targets compile for iOS here, but the native Swift host, Xcode asset
catalog, and iOS launch screen need a macOS/Xcode build for verification.
