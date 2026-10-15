# Authentication design

Native Android Views implementation: `AuthEntryView.kt`.

Figma file: `OFMvU7yImxJFUpV7e061Pj`.

| Screen | Node |
| --- | --- |
| Entry | `430:1129` |
| Login sheet | `21:52` |
| Registration sheet | `7:261` |

The entry logo uses `drawable/auth_logo.xml`, converted from the user-supplied
`Group 6.svg`. Its eight paths, white fills, stroke, lettering and 342 × 265
viewport are preserved. The logo is a single vector including the KAIROS name.

Both popup backgrounds are native white `GradientDrawable` shapes with 36-unit
top corner radii and square bottom corners. Their centered 111 × 5 green handles
are native rounded shapes too. These elements do not use raster images.

The entry, login and registration text use the bundled Montserrat variable font,
with explicit weights for headings, fields and actions. Onboarding, loading and
the account-completion/recovery theme use the same family. Inter remains bundled
for compatibility with existing assets; its license is retained in `docs/licenses`.
The panel contents scroll when the keyboard or available screen height requires it.
Passwords are deliberately excluded from saved instance state.

The onboarding waits for session restoration: it is only eligible for an idle,
unauthenticated login/registration flow. Restored users go directly to Home or
profile completion. Completing the onboarding is retained during activity
recreation; returning from an authenticated session to login starts a new entry
flow. If restoration finishes during the splash, onboarding is inserted below it.
`OnboardingStyle` applies Montserrat weights after inflation (titles/actions 700,
body/counter 600), preventing the AppCompat theme from selecting the font's Thin
default. Onboarding uses a scrollable, centered content column and a separate
bottom action; text and button share the same width on common phone sizes.
The system-bar insets are reserved even during the splash, explicitly requested
after attachment, and the button can grow with accessibility font scaling.
`OnboardingLayoutTest` covers all three titles at 320×568, 360×640 and 412×915dp,
with normal and enlarged text. `AuthUiPreviewTest` renders an isolated preview.

The entry buttons use 58-unit heights and 20sp semibold text. Login/registration
use compact 58-unit fields (16sp), 16-unit gaps and a 56-unit primary action
(18sp), with green focus borders and flat ripple buttons without native elevation.
These dimensions scale down on narrow phones, are capped at the reference width
on larger screens, and keep the existing keyboard-aware scrolling and validation.
The latest refinements use the previously retrieved reference and user feedback;
a fresh Figma comparison is pending because the connector's Starter quota is exhausted.

Registration creates the Firebase identity first and then opens the existing
profile-completion screen for name, surname, birth date, CPF, ZIP code and plan.
No profile values are invented or defaulted. Completing the profile reaches the
stocker Home described in `stocker-home.md`. Existing users with a complete profile reach it
directly after login. Password recovery still uses the existing email-link flow.

The service-terms link currently displays an explicit unavailable-content notice:
the project does not provide approved terms or a terms URL. Supply that content
before releasing this registration flow.
