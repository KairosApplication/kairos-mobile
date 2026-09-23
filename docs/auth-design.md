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

Montserrat and Inter are bundled locally with their OFL licenses in `docs/licenses`.
The panel contents scroll when the keyboard or available screen height requires it.
Passwords are deliberately excluded from saved instance state.

Registration creates the Firebase identity first and then opens the existing
profile-completion screen for name, surname, birth date, CPF, ZIP code and plan.
No profile values are invented or defaulted. Completing the profile reaches the
existing temporary home screen. Existing users with a complete profile reach it
directly after login. Password recovery still uses the existing email-link flow.

The service-terms link currently displays an explicit unavailable-content notice:
the project does not provide approved terms or a terms URL. Supply that content
before releasing this registration flow.
