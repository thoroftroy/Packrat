# Packrat changelog

## v0.7.1

- The moving background holds still while Battery Saver is on, so it uses next to no power then. It still changes with what the app is doing

## v0.7.0

- The background has a box for each thing you add to an archive (or each file in one you open); they hurry while it works and a stack builds up as files come out
- The background answers your finger: a tap sends rings out and pops what it lands on, and dragging or scrolling leaves a wake and stirs the scene. Saving, adding, deleting and finishing things send a wave of colour through it, and an error gives a red shudder
- The animated background now has a picture of its own behind everything: parcels drifting up. It is faint, so text stays easy to read
- Aqua changes far more through the day: a rose dawn, gold sunrise, turquoise morning, deep azure noon, warm afternoon, orange sunset, violet dusk and a real dark night with the moon shimmering through the water and glowing plankton. Now and then a cloud passes over and the light dims
- Super's space changes with the time of day too: a dark night with brighter stars, a rose dawn, teal morning, cobalt noon and a red and amber sunset
- Opening the app no longer sprays a burst of fast symbols across the background; celebrations are a gentle lift of a few symbols, and only for something you just did

## v0.6.0

- The background gets busy with tumbling boxes while an archive is packed or unpacked, and celebrates when the files are out

## v0.5.18

- The symbols drifting in the background are smaller and fainter so they no longer crowd text; selected segments and chips are readable in every look (including Super); the warning banner at the top has a solid backing so its text is easy to read

## v0.5.17

- The animated background is now drawn directly on the screen at full sharpness instead of a small picture stretched up, so it is smooth and no longer looks pixelated; the grain over it is much subtler, and long tab names (like Feedback) shrink to fit instead of being cut off

## v0.5.16

- At night the animated background gets a stronger wash, so text stays easy to read on dark scenes

## v0.5.15

- Each app's animated scene now has its own drifting symbols (maths signs in Abacus, notes in Echo, arrows in Wayfinder and so on), and the scene is a little clearer while text stays easy to read
- Animated backgrounds reworked after your report: they are drawn at a higher resolution and smoothly at 24 frames a second without slowing the app down, and no longer show horizontal lines

## v0.5.14

- Every app window now has the animated scene behind it, in the app's own colour (for example orange water in Abacus, green in Pulse, purple in Scribble), following your Aqua, Bliss or Super look and the real sunrise and sunset. Motion Reduced shows a still picture and Off turns it off

## v0.5.13

- Fixed a gap above the keyboard in screens with a bottom tab bar: the tab bar now steps aside while you type, so you can see what you are writing

## v0.5.12

- New settings: sort files inside an archive by name, size or date, and whether folders come first

## v0.5.11

- New settings: how many recent archives to remember and the compression a new archive starts with
- Text size and accent colour chosen in Control Room now apply here
- New Rotate with the phone switch in Settings

## v0.5.10

- Loading screens now say what they are doing instead of showing a bare spinner
- Wording tidied so no sentence needs a dash

## v0.5.9

- Create archives in more formats: ZIP, 7Z, TAR.GZ, TAR.XZ and TAR, each with a plain-language explanation of when to use it
- Choose how hard to compress (None, Fastest, Normal, Smallest) and protect a ZIP with an AES-256 password

## v0.5.8

- Opens 7z archives (including password-protected ones)
- Opens RAR archives (RAR 4 and older)
- Opens password-protected ZIPs (AES-256 and classic ZipCrypto): Packrat asks for the password, and asks again if it's wrong

## v0.5.7

- Lighter on the battery: in the Super look the drifting sparkles settle after a few seconds without a touch (the next tap wakes them), so a screen you're reading no longer redraws 25 times a second

## v0.5.6

- Changing the look in Control Room now restyles this app's widgets straight away, with no refresh or restart

## v0.5.5

- Polish and fixes

## v0.5.4

- Fixes from a full round of stress testing on every screen

## v0.5.3

- Tap the tab you're already on to glide back to the top of its list

## v0.5.2

- Smoother everywhere: new screens, tabs and Settings ease in instead of snapping, and buttons, tabs, chips and switches give a light tap you can feel (following your phone's touch-feedback setting)
- Android's predictive back: the back gesture shows a live preview of where you're going
- Super look: tap sparkles now match Bliss Player: soft glowing sparks with trails, drifting rainbow colours and a faint aurora, instead of big solid circles

## v0.5.1

- Much faster and smoother: the app is now built optimised (the way Play Store apps are), so screens open quicker, scrolling is fluid and the APK is about a quarter of the size
- Screens and lists no longer decode pictures on the main thread, and skip redrawing parts that didn't change

## v0.5.0

- New icon
- Fixed a crash when a recent archive came from an app that no longer lets Packrat read it
- Archives that contain the same path twice no longer crash the list
- Works properly in landscape: tabs move to a rail at the side so the content keeps its height, and screens that ran out of room now scroll
- If the app ever crashes it says so next time it opens and offers to send a report (only the error, app version and phone model, with file names and links removed). Turn on automatic sending in Control Room › Wonderdesk
- Lists can no longer crash when two items share a name

## v0.4.0

- Polish and stability fixes from the suite-wide test pass
- If the app ever crashes or freezes, a report goes to Control Room so it can be shared and fixed
- App icon art is larger and fills its tile without being cut off

## v0.3.1

- App icon redrawn so nothing is cut off: round icons fill the space, the rest sit on an Aqua tile
- Smoother and lighter on the battery: the window background no longer animates, tap effects sleep until you tap, and nothing ticks while the app is in the background
- Opening the app no longer waits on Control Room to read the suite look

## v0.3.0

- Opens ZIP, TAR and GZ archives from Wayfinder
- Browse archives like folders, extract what you pick, create ZIPs
