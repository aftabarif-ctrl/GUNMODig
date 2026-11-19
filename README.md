# Modern Arms (Fabric, Minecraft 1.21.1)

Pistol, Sniper Rifle and RPG Bazooka, all fed by one item: **Universal Ammo**.

## Build
Requires JDK 21. Open the folder in IntelliJ IDEA (Gradle import) or run:

    gradle wrapper --gradle-version 8.12    # once, to generate ./gradlew
    ./gradlew build                         # jar -> build/libs/modernarms-1.0.0.jar
    ./gradlew runClient                     # test in a dev client

If a version in `gradle.properties` is no longer on Fabric's Maven, bump `fabric_version`
(and `yarn_mappings` if needed) from https://fabricmc.net/develop/.

## Controls
| Weapon | Fire | Aim | Reload |
|---|---|---|---|
| Pistol | Right-click (hold) or left-click | - | R |
| Sniper | Left-click | Hold right-click (scope) | R (bolt cycles automatically after each shot) |
| RPG    | Left-click | Hold right-click (sight) | R (auto-reloads after firing if you carry ammo) |

One Universal Ammo item refills a whole magazine (pistol 12, sniper 5) or loads one rocket.
Creative mode doesn't consume ammo. Craft ammo: gunpowder + copper ingot + iron nugget -> 8.

## Tuning
* Gameplay numbers (damage, cooldown, reload, range, spread, magazine): `WeaponType.java`
* Rocket power / speed / knockback radius: `RocketEntity.java`
* Animation curves (recoil, reload, bolt, sway, aim offsets): `ClientFx.applyGunTransform`
* Zoom factors: `ClientFx.fovMultiplier`
* Held-weapon placement (position/scale in hand): `display` blocks in `assets/modernarms/models/item/*.json`

## Design notes
* Hitscan + damage, reloads, cooldowns, rocket and explosion are server-authoritative; the client only animates/draws.
* Rocket explosions use vanilla `createExplosion(... TNT ...)`, so block damage follows normal rules (gamerules, blast resistance, drops).
* The rocket is rendered with particles (flame core + smoke trail), so there's no entity model to maintain.
* Sounds are vanilla sounds re-pitched/layered, with scheduled echoes; no audio files needed.

## Port notes (1.21.4 -> 1.21.1)
* Removed `assets/modernarms/items/*.json` (client item definitions are 1.21.2+; 1.21.1 reads `models/item/*.json` directly).
* Recipes use `{"item": ...}` ingredient objects; item/entity registration without `registryKey`; `use()` returns `TypedActionResult`; `damage(source, amount)`.
* Rocket uses a tiny no-op `RocketRenderer` (vanilla `EmptyEntityRenderer` is 1.21.2+).
* Re-equip suppression moved from a mixin to `GunItem.allowComponentsUpdateAnimation` (Fabric API).
