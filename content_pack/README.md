# Ultra Enchantment ascension pack

Links the **real tiered enchantments** added by these mods into ascension trees:

- Just a lot more enchantments (`jlme`)
- Fancy Enchantments (`fancyenchantments`)

**No enchantment is copied or generated.** Every tree node is an enchantment
that already exists in the vanilla or mod registry, so all source-mod behavior
(code-driven or data-driven) is preserved.

## Trees (8)

| Vanilla root | Branches |
|---|---|
| `minecraft:sharpness` | JLME `advanced → supreme`; Fancy `advanced → sacred supreme` |
| `minecraft:bane_of_arthropods` | JLME `advanced → supreme` |
| `minecraft:smite` | JLME `advanced → supreme` |
| `minecraft:looting` | JLME `advanced`; Fancy `advanced → greed supreme` |
| `minecraft:power` | JLME `advanced` |
| `minecraft:protection` | JLME `advanced`; Fancy `advanced → mountain supreme` |
| `minecraft:fire_aspect` | Fancy `advanced` |
| `minecraft:flame` | Fancy `advanced` |

Tier mapping: vanilla root = native, `advanced_*` = advanced, `supreme_*` =
super. The ultra tier stays defined for future content but is unused here.

## Install

1. Install **Just a lot more enchantments** and **Fancy Enchantments**
   for 1.21.1 NeoForge.
2. Put this folder in `world/datapacks/ultraenchantment_integration/`.
3. Install Ultra Enchantment and place a Starlight Enchanting Table.

## Progression

- Root → advanced: vanilla enchantment at its max level; 100% success;
  base 6 XP levels; **5 diamonds**.
- Advanced → supreme: advanced enchantment at level 3 (or its max);
  60% success; base 10 XP levels; **2 netherite ingots**.
- XP comes from the target tier's `evolution_xp` (advanced 6 / super 10 /
  ultra 18). Bookshelves placed around the Starlight Table like a vanilla
  enchanting room (up to 15) cut XP linearly, up to 50%: with a full shelf
  wall the costs above become 3 / 5 / 9.
- Failure consumes XP and materials (no refund) and drops the current
  enchantment 2–4 levels (minimum 1).

You can tune the generated tree JSONs directly; re-run
`tools/generate_integration_pack.py` after updating the mod jars.
