# Silk & Fortune Glow (Fabric, Minecraft 26.3)

Client-side. Makes Silk Touch and Fortune items instantly distinguishable:

| | Silk Touch | Fortune |
|---|---|---|
| Inventory / hotbar slot | cyan light sweeping around the slot border | pulsing gold border, twinkling corners |
| Held in hand (any player) | blue soul-flame wisps | gold sparkles |

Applies to any item (and enchanted books) with Silk Touch or Fortune.

## Doesn't touch the item skin
Everything is drawn in the 1px slot border AROUND the item, never over its pixels. No models, shaders or glint are
changed, so it works with the Enchantment Outlines resource pack, and alongside the Enchant Tag mod (separate mod id,
no shared state, no mixins).

## Build
Easiest: upload this folder to a GitHub repo, open Actions, download the artifact, use the jar WITHOUT -sources.
Or locally with JDK 25: `gradle wrapper --gradle-version 9.6.0` then `./gradlew build`.

## Install
Jar into `.minecraft/mods` next to Fabric API 0.161.0+26.3 (and Enchant Tag if you have it). Launch the Fabric profile.
