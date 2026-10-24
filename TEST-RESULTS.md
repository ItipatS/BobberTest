# Test results

## Server version compatibility

The same Java 21 jar was tested on both ends of the supported range:

| Server | Java | Result |
| --- | --- | --- |
| Paper 1.21.11 build 132 | 21 | Loaded 6 skins; cast created 1 tracked hook and 1 display; disconnect cleaned both up |
| Paper 26.1.2 build 74 | 25 | Loaded 6 skins; cast created 1 tracked hook and 1 display; disconnect cleaned both up |

Both servers also passed plugin enable/disable, `/bobber stats`, and `/bobber reload` without plugin errors. The
resource pack metadata supports formats 75 through 84, covering 1.21.11 through 26.1.2. A real 1.21.11 client also
accepted the server pack and rendered the custom, recolourable bobbers correctly.

## Performance and behaviour

**Setup:** Velocity 4.3.0 in front of AdvancedSlimePaper 26.1.2 (Java 25) on a desktop PC (Ryzen 5 5600X). Players
are bots on the real 26.1 protocol fishing at one pond, plus a real 26.1.2 client. Performance tests were run
with the plugin installed and again with it removed, so the difference is the plugin's cost.

## Server ticks

| Test | Without plugin | With plugin |
| --- | --- | --- |
| 50 players fishing, 120 s | 2.83 ms | 2.83 ms |
| 30 players fishing, 60 s | 2.36 ms | 2.45 ms |

The budget is 50 ms per tick, so the plugin costs under 0.1 ms. The server doesn't update bobbers every tick; it
only acts when something happens (cast, bite, catch, animation step).

## Packets

What one player standing at the pond receives while 30 players fish around them (about 26 bobbers in view), measured
over 60 s:

| | Without plugin | With plugin | Difference |
| --- | --- | --- | --- |
| Packets per second | 661 | 741 | +80 |
| Data per second | 18.3 KB | 21.2 KB | +2.9 KB |

That's about **3 packets and 0.1 KB per second per visible bobber**: an animation step is one small packet, and the
bobber follows its hook on the client for free. Players only receive bobbers within view distance, and players
without the resource pack receive none.

## Client side

- Recording with 30 custom bobbers in view, plus shaders (Iris + Complementary), held **138 FPS** (vsync cap 140) on
  an RX 5700 XT.
- Models are tiny: the largest is 9 cubes (54 faces), about a simple block. The whole pack is 13 KB.
- Texture animations (flapping, flicker, shimmer) run entirely on the client and cost the server nothing.
- No client mods needed. Players who decline the resource pack see the normal vanilla bobber, never a broken cube.

## Colours per player

- Each player's colours are stored on that player and sent as part of their bobber's item, one colour per
  recolourable part. The client applies them to the model's gray texture.
- Automated checks: a bobber always carried exactly the colours set for its player (or its rod), and with 30 players
  fishing at once, bobbers of the same type showed different colours side by side.
- **Recolouring a bobber that's already in the water** updates it straight away, for everyone watching, with a single
  packet.
- A bobber can also flash bite colours (the lantern flares on a bite) and return to the player's colours after.

## No leftovers

Every way a hook can disappear was tested, and no bobber was ever left behind: reel in, catch, hook in the ground,
switching hotbar slot, line breaking, teleporting away, changing world, `/kill`, logging out, switching server, and
restarting the server.
