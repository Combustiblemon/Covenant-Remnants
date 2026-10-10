# Implementation — crash on first shield raise near damaged ships (2026-10-10)

The developer fired plasma torpedoes for a while, then raised the Covenant shield, and the game
crashed:

```
org.lwjgl.opengl.OpenGLException: Cannot use Buffers when Array Buffer Object is enabled
	at org.lwjgl.opengl.GLChecks.ensureArrayVBOdisabled(GLChecks.java:71)
	at org.lwjgl.opengl.GL11.glTexCoordPointer(GL11.java:3136)
	at com.fs.graphics.F.o00000(Unknown Source)
	at com.fs.starfarer.renderers.damage.String.o00000(Unknown Source)
	at com.fs.starfarer.combat.entities.Ship.render(Unknown Source)
```

## Root cause

- **Vanilla draws armor damage decals with client-side arrays.** The decal renderer
  (`renderers.damage.String`) batches its decals in `com.fs.graphics.F`. `F` has a VBO path and a
  client-array path, and vanilla `settings.json` has `"forceNoVBO": true`, so decals always go
  through `glTexCoordPointer(…, FloatBuffer)`.
- **LWJGL 2 refuses client arrays while it believes an array buffer is bound.** The game's
  `lwjgl.jar` was decompiled to check how it tracks the binding:
  - `StateTracker` records the `GL_ARRAY_BUFFER` binding only from `glBindBuffer` /
    `glBindBufferARB` and from the client-attrib stack.
  - `glDeleteBuffers` and `glPushAttrib` / `glPopAttrib` do not touch it.
  - `GLChecks.ensureArrayVBOdisabled` throws if the tracked binding is not 0.
- **`ShieldShader.initQuad()` left its VBO bound.** It bound the VBO to set up its VAO, then
  unbound only the VAO.
  - `CovenantShieldsStats.apply()` creates the shield render plugin (and with it the
    `ShieldShader`) the first time a ship raises its shield in a battle. That happens during the
    engine's advance, before the frame is drawn.
  - The shield only drew itself, and its quad update unbound the buffer, on the
    `ABOVE_SHIPS_LAYER`, after the ships.
  - So on the first shield raise of a battle, every ship drawn that frame ran with the VBO still
    bound. Any ship with armor damage decals crashed the game.
- **Why it showed up only now:** earlier shield tests had no damaged ships on screen at the first
  raise. The torpedo test (with the armor melt) left damaged ships everywhere before the shield
  went up. Every other shader in the mod (`RuptureSpriteShader`, `RuptureSphereShader`,
  `ArkTubeShader`, `BlinkRippleShader`) already binds 0 after its setup.

## Fix

`src/.../utils/render/ShieldShader.kt` — `initQuad()` ends with `glBindBuffer(GL_ARRAY_BUFFER, 0)`
after `glBindVertexArray(0)`. The VAO keeps its own reference to the buffer, so drawing is
unchanged.

Checked: every other buffer bind in the mod and in LemonLib is inside a save / restore wrap
(`SpriteGlState`, `RuptureRenderer`, `BlinkRenderer`, `ShieldShader.render`) or followed by an
unbind. `gradlew buildMod` green.

## In-game check

1. Damage some enemy armor first (torpedoes, any weapon), then raise a Covenant shield for the first
   time in that battle: no crash; the shield draws as before.
2. Same with an AI Covenant ship raising its shield near damaged ships.

## Note

Rule for any new GL code in the mod: after setting up or updating a VBO outside a save / restore
wrap, bind `GL_ARRAY_BUFFER` back to 0. Never delete a buffer while it is bound: LWJGL keeps
believing it is bound and the next vanilla decal draw crashes.
