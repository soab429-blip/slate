# Slate
Slate is a Slinky-based ghost client for Ornithe 1.8.9, meant to provide a high-quality, reliable experience for any user. It has modules much more refined than even Slinky's, ensuring you can remain undetected even in high-stakes situations (for instance ranked Bed Wars).

## TO-DO
- Value improvements:
  - Add collapsible values, along with support for it in the JSON config file.
  - Improve the code for values, allowing it to be in one single file without much trouble.
- Auto block improvements:
  - Avoid it blocking in the first hit (helps with not getting comboed while using timer range)
- Bridge assist improvements:
  - Add pre-sneaking (detects when you rotating to a 45º angle and holding the right keys; sneaks until you stopped rotating for 2-3 ticks).
  - Add an option to automatically strafe in order to align, while diagonally bridging (much faster than stopping block placements to do such).
- Auto tool improvements:
  - Prevent activating while fighting & not targeting a bed defense (helps prevent an issue that got Kornelic caught for cheating).
- Instant stop improvements:
  - Make it work with strafing, instead of simply forward/backward.
- Sprint reset improvements:
  - Add a timing mode, with defensive, offensive, and dynamic. Defensive sprint resets right after you get hit, offensive remains as-is (right after you hit), and dynamic picks between one and the other in each hit.
