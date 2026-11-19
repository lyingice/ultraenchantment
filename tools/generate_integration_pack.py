"""
Generate the Ultra Enchantment ascension pack for:
  - Just a lot more enchantments (jlme)
  - Fancy Enchantments (fancyenchantments)

Both mods add REAL tiered variants of vanilla enchantments. This pack links
those real enchantments into one ascension tree per vanilla root. It does NOT
copy, clone or generate any enchantment: every node points at an enchantment
that already exists in the mod / vanilla registry.

Example:

  minecraft:sharpness
      ├─ jlme:advanced_sharpness ──> jlme:supreme_sharpness
      └─ fancyenchantments:advanced_sharpness
             └─> fancyenchantments:sacred_supreme_sharpness

Tier mapping:
  native  = vanilla root
  advanced = the mod's advanced_* variant
  super    = the mod's supreme_* / *_supreme_* variant

Run from the project root:
  py tools/generate_integration_pack.py
"""

import json
import pathlib
import shutil
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "content_pack"

MODS = {
    "jlme": ROOT / "build/tmp/mods/jlme.jar",
    "fancyenchantments": ROOT / "build/tmp/mods/fancy.jar",
}

# Vanilla roots and their max levels. Branches are ordered left-to-right in
# the star map; every id listed here must exist in the corresponding mod jar.
FAMILIES = [
    {
        "root": "minecraft:sharpness",
        "root_max": 5,
        "branches": [
            ("jlme", ["jlme:advanced_sharpness", "jlme:supreme_sharpness"]),
            ("fancyenchantments", [
                "fancyenchantments:advanced_sharpness",
                "fancyenchantments:sacred_supreme_sharpness",
            ]),
        ],
    },
    {
        "root": "minecraft:bane_of_arthropods",
        "root_max": 5,
        "branches": [
            ("jlme", [
                "jlme:advanced_bane_of_arthropods",
                "jlme:supreme_bane_of_arthropods",
            ]),
        ],
    },
    {
        "root": "minecraft:smite",
        "root_max": 5,
        "branches": [
            ("jlme", ["jlme:advanced_smite", "jlme:supreme_smite"]),
        ],
    },
    {
        "root": "minecraft:looting",
        "root_max": 3,
        "branches": [
            ("jlme", ["jlme:advanced_looting"]),
            ("fancyenchantments", [
                "fancyenchantments:advanced_looting",
                "fancyenchantments:greed_supreme_looting",
            ]),
        ],
    },
    {
        "root": "minecraft:power",
        "root_max": 5,
        "branches": [
            ("jlme", ["jlme:advanced_power"]),
        ],
    },
    {
        "root": "minecraft:protection",
        "root_max": 4,
        "branches": [
            ("jlme", ["jlme:advanced_protection"]),
            ("fancyenchantments", [
                "fancyenchantments:advanced_protection",
                "fancyenchantments:mountain_supreme_protection",
            ]),
        ],
    },
    {
        "root": "minecraft:fire_aspect",
        "root_max": 2,
        "branches": [
            ("fancyenchantments", ["fancyenchantments:advanced_fire_aspect"]),
        ],
    },
    {
        "root": "minecraft:flame",
        "root_max": 1,
        "branches": [
            ("fancyenchantments", ["fancyenchantments:advanced_flame"]),
        ],
    },
]

TIER_NATIVE = "ultraenchantment:native"
TIER_ADVANCED = "ultraenchantment:advanced"
TIER_SUPER = "ultraenchantment:super"

# Edge settings, keyed by target stage index (0 = advanced, 1 = super).
# 经验代价不再写在边上：由阶级 JSON 的 evolution_xp 定义（高阶 6 / 超级 10 / 究极 18），
# 台面书架再打折（满 15 书架减半）。边只负责前置等级与成功率。
CHANCES = [1.0, 0.6]


def load_enchantments():
    """modid -> {name: json} for every root-level enchantment file."""
    result = {}
    for modid, jar_path in MODS.items():
        if not jar_path.exists():
            raise SystemExit(f"missing mod jar: {jar_path}")
        with zipfile.ZipFile(jar_path) as jar:
            prefix = f"data/{modid}/enchantment/"
            entries = {}
            for name in jar.namelist():
                if not name.startswith(prefix) or not name.endswith(".json"):
                    continue
                relative = name[len(prefix):-len(".json")]
                if "/" in relative:
                    continue
                with jar.open(name) as handle:
                    entries[relative] = json.load(handle)
            result[modid] = entries
    return result


def validate(enchantments):
    for family in FAMILIES:
        for branch_name, stages in family["branches"]:
            jar_entries = enchantments[branch_name]
            for enchantment_id in stages:
                namespace, path = enchantment_id.split(":", 1)
                if namespace != branch_name:
                    raise SystemExit(
                        f"{enchantment_id} does not belong to mod {branch_name}")
                if path not in jar_entries:
                    raise SystemExit(f"missing enchantment in {branch_name}: {enchantment_id}")
                max_level = int(jar_entries[path].get("max_level", 1))
                if max_level < 1:
                    raise SystemExit(f"bad max_level on {enchantment_id}: {max_level}")


def node_id(tree_id, suffix):
    return f"{tree_id}/{suffix}"


def build_tree(family, enchantments):
    root_id = family["root"]
    root_path = root_id.split(":", 1)[1]
    tree_id = f"ultraenchantment:integration/{root_path}"
    branches = family["branches"]
    x_offset = 0.0 if len(branches) == 1 else 1.0

    nodes = [
        {
            "id": node_id(tree_id, "root"),
            "enchantment": root_id,
            "tier": TIER_NATIVE,
            "pos": {"x": 0.0, "y": 0.0},
        }
    ]
    edges = []

    for branch_index, (branch_name, stages) in enumerate(branches):
        x = 0.0 if len(branches) == 1 else (-x_offset if branch_index == 0 else x_offset)
        previous = node_id(tree_id, "root")
        previous_max = family["root_max"]

        for stage_index, enchantment_id in enumerate(stages):
            if stage_index == 0:
                suffix = f"{branch_name}_advanced"
                tier = TIER_ADVANCED
            elif stage_index == 1:
                suffix = f"{branch_name}_supreme"
                tier = TIER_SUPER
            else:
                raise SystemExit(
                    f"{enchantment_id}: only two upgrade stages are supported per branch")
            current = node_id(tree_id, suffix)
            nodes.append(
                {
                    "id": current,
                    "enchantment": enchantment_id,
                    "tier": tier,
                    "pos": {"x": x, "y": float(stage_index + 1)},
                }
            )
            edges.append(
                {
                    "from": previous,
                    "to": current,
                    "required_level": (
                        family["root_max"] if stage_index == 0
                        else max(1, min(3, previous_max))
                    ),
                    "chance": CHANCES[stage_index],
                }
            )

            previous = current
            path = enchantment_id.split(":", 1)[1]
            previous_max = int(enchantments[branch_name][path].get("max_level", 1))

    return tree_id, {
        "display": f"enchantment.minecraft.{root_path}",
        "nodes": nodes,
        "edges": edges,
    }


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    if OUT.exists():
        shutil.rmtree(OUT)

    enchantments = load_enchantments()
    validate(enchantments)

    write_json(
        OUT / "pack.mcmeta",
        {
            "pack": {
                "pack_format": 48,
                "description": "Ultra Enchantment ascension: JLME + Fancy Enchantments",
            }
        },
    )

    tree_count = 0
    branch_count = 0
    for family in FAMILIES:
        tree_id, tree = build_tree(family, enchantments)
        tree_path = tree_id.split(":", 1)[1]
        write_json(
            OUT / "data/ultraenchantment/ultraenchantment/starlight_tree"
            / (tree_path + ".json"),
            tree,
        )
        tree_count += 1
        branch_count += len(family["branches"])

    readme = f"""# Ultra Enchantment ascension pack

Links the **real tiered enchantments** added by these mods into ascension trees:

- Just a lot more enchantments (`jlme`)
- Fancy Enchantments (`fancyenchantments`)

**No enchantment is copied or generated.** Every tree node is an enchantment
that already exists in the vanilla or mod registry, so all source-mod behavior
(code-driven or data-driven) is preserved.

## Trees ({tree_count})

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
"""
    (OUT / "README.md").write_text(readme, encoding="utf-8")

    print(f"generated {tree_count} trees / {branch_count} branches (no copied enchantments)")


if __name__ == "__main__":
    main()
