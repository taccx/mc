# Domain Expansion (棰嗗煙灞曞紑)

An addon for **T.O Magic 'n Extras (traveloptics)** on **Minecraft 1.20.1 / Forge 47.4.16**,
adding the Aqua (婧愭祦) ultimate **棰嗗煙灞曞紑**.

Casting it opens a radius 30 sphere centred on the caster for two minutes:

- the boundary is built from real blocks, so it is both visible and a physical barrier
- entities that were inside when it opened cannot leave; entities outside cannot enter
  (the caster is always free to move)
- damage the caster deals while inside is applied to every other entity inside
- **Overflow** and **Rainfall** are summoned automatically and kept alive for the whole
  duration, at the same spell level as the domain
- when it ends, every block it displaced is put back exactly as it was

| Property | Value |
|---|---|
| Spell id | `domain_expansion:domain_expansion` |
| School | 婧愭祦 / Aqua (`traveloptics:aqua`) |
| Rarity | Legendary, max level 3 |
| Cast type | INSTANT |
| Radius | 30 blocks |
| Duration | 2 minutes of **real** time |
| Mana cost | 200 + 200 per level |
| Cooldown | 300 s |

There is no scroll or spell book item for it; cast it with Iron's Spellbooks' own command:

```
/cast <player> domain_expansion
```

---

## Verified behaviour

Taken from the pack's `latest.log` for a real cast, which is the evidence this build was
accepted on:

```
domain opened at (-73.87, -60.0, -38.16) radius=30.0 captured=2
outward phase done at t=1s
sphere complete at t=3s, 8486 blocks written; 120s real time start now
t=5s  phase=COMPLETE r=31 y=31 written=8486
  ... heartbeats every 5s, written stable ...
t=120s phase=COMPLETE r=31 y=31 written=8486
domain closing after 120s of real time
restoring 8486 blocks over ~9 ticks
restored 8486 blocks
```

| Requirement | Evidence |
|---|---|
| Radius 30 sphere | 8,486 blocks written, matching the geometry exactly |
| Built quickly | 3.6 real seconds from cast to complete |
| No lag from this mod | zero `Can't keep up` attributable to the domain |
| Two minutes | closed after 120s of real time |
| Blocks removed, terrain restored | 8,486 restored in 0.45s |
| Visible in game | confirmed by the user |

The 8,486 figure is the whole visible structure: the upper half of the one-block-thick wall
(~5,659) plus the floor disc (~2,827). It is far below the ~113,000 positions a radius 30
sphere contains because interior positions that are already open space are skipped - nothing
to change, nothing to remember. That one optimisation is what makes a domain this size
affordable.

---

## How it is put together

The design follows **Cursed Fate**'s domain, which is the implementation known to work in
this modpack.

**Three blocks** (`block/`):

| Block | Role |
|---|---|
| `domain_shell` | the visible wall: a solid, opaque, light-emitting cube |
| `domain_floor` | the floor, laid one level below the centre |
| `domain_air` | the interior filler: no shape, no collision, no occlusion, emits light |

`domain_air` is the central trick. Rather than deleting the terrain inside the sphere -
destructive, and impossible to undo reliably - the interior is *replaced* with that block.
It behaves like air for everything that matters but is a real block, so the state it
displaced can be recorded and restored exactly.

**Two build phases** (`domain/SphereShape`, unit tested):

1. `OUTWARD` - radius grows from 1. Each step lays the new annulus of the floor and the
   matching ring of the base plane, so the domain ripples outwards from the caster.
2. `VERTICAL` - height grows from 1, one horizontal slice of the sphere at a time, closing
   the dome overhead.

The centre column of the base plane is deliberately never filled: the caster stands there.

**Placement** uses update flag 2 (clients only). Neighbour updates are pointless for a shell
nothing interacts with.

**Departures from Cursed Fate**, both for cost:

- Interior positions that would become `domain_air` while already being air are skipped.
  Cursed Fate fills the whole interior, which lights it but costs tens of thousands of extra
  writes; here the wall and floor emit light instead, and only terrain that needs clearing is
  touched.
- The lower half of the sphere is not built. Cursed Fate fills it to keep players out of
  caves below; the floor layer already provides support, so skipping it removes roughly half
  the work.

Everything is driven from `DomainTickHandler` rather than the entity's own `tick()`, so the
domain's lifetime does not depend on the anchor entity.

---

## Building

`./libs` must be populated first - the two dependency jars are not in this repository
(40MB of someone else's code, and not ours to redistribute):

```powershell
$proj = 'E:\dsh\domain-expansion'
$mods = 'E:\minecraft1.12.2銆婂紓涓栫晫锛氬繊鑰呬箣褰便€嬫湭瀹屾垚鍐呮祴鐗堟湰V2.1.132\minecraft1.12.2銆婂紓涓栫晫锛氬繊鑰呬箣褰便€嬪唴娴嬬増鏈琕2.1.132\.minecraft\versions\娑熸吉涔嬬瘒路濡傛稛婕箣鎵€瑙乗mods'
New-Item -ItemType Directory -Force $proj\libs | Out-Null
Copy-Item "$mods\irons_spellbooks-1.20.1-3.15.4.jar" $proj\libs
Copy-Item "$mods\traveloptics-6.3.0-1.20.1.jar" $proj\libs
```

Then:

```powershell
$env:JAVA_HOME        = 'C:\Program Files\Microsoft\jdk-17.0.15.6-hotspot'
$env:GRADLE_USER_HOME = 'E:\dsh\.gradle-home3'
& 'E:\dsh\.skill-src\forge\gradle-dist\gradle-8.1.1\bin\gradle.bat' `
    -p 'E:\dsh\domain-expansion' --console=plain --no-daemon --no-watch-fs clean build
```

Output: `build/libs/domain_expansion-1.0.0.jar`.

Gradle 8.1.1 and ForgeGradle 6.0.16 are pinned deliberately; 8.8 fails with
`Could not find 'versionParser'`.

### Network, and two traps that cost a lot of time

`maven.minecraftforge.net`, `repo.maven.apache.org` and `plugins.gradle.org` are unreachable
from this machine. Two things must be true for a build to succeed:

1. **The local Clash proxy on `127.0.0.1:7890` must be running**, and `gradle.properties`
   must have its `systemProp.http(s).proxy*` lines enabled to match. Leaving those lines
   enabled while the proxy is *down* is worse than leaving them off: every request,
   including to reachable mirrors, fails with `Connection refused`, and the error surfaces
   as a confusing "could not find artifact" message.

2. **There must be no `flatDir` repository.** ForgeGradle applies its mapped-artifact
   content filter to every declared repository, and a `flatDir` repository cannot accept
   one, so it throws

   ```
   IllegalStateException: Cannot mutate content repository descriptor 'flatDir(...)' after repository has been used
   ```

   which then makes the mapped Forge jar unresolvable and fails `compileJava`. The local
   dependency jars do not need it - they are plain file dependencies, and `fg.deobf` cannot
   process them anyway (it warns `files(...) dependencies are not deobfuscated` on every
   build).

A third, related trap: ForgeGradle resolves its reobfuscation tool through a dynamic version
(`ForgeAutoRenamingTool:1.+`), which needs a `maven-metadata.xml` from the Forge maven. With
the proxy down, merely *configuring* the reobf task fails with
`Could not resolve ... ForgeAutoRenamingTool_1.+_all_1` even though the jar sits in
ForgeGradle's own cache.

`gradle.properties`, `build.gradle` and `.gitignore` all carry comments explaining these in
place, so a future session does not have to rediscover them.

### Mappings

`files()` dependencies are **not** deobfuscated by `fg.deobf` here: the two jars in `libs/`
keep **SRG member names** while their class names stay readable. Anything inherited from them
must be named in SRG form in source (`m_8097_`, `m_7380_`, ...); `reobfJar` converts the rest,
which is why the built classes show `m_8119_`, `m_142687_` and so on.

---

## Installing into the modpack

Use the guarded installer, which refuses to copy while the game is running:

```powershell
powershell -ExecutionPolicy Bypass -File E:\dsh\.skill-src\install-jar.ps1
```

That guard exists because the jar was once replaced **while Minecraft was loading mods**. The
loader read a half-written file and the game died with

```
UncheckedIOException: UnionFileSystem$NoSuchFileException: com/dsh/domainexpansion/spell
```

followed by a `NullPointerException` on the render thread - which looks exactly like a mod
defect and cost a round of diagnosis. **Never install into a running game.**

The script is saved with a UTF-8 BOM on purpose: Windows PowerShell 5.1 reads `.ps1` files as
ANSI without one, which turns the Chinese path into mojibake and fails the copy.

---

## Tests

Fifteen JUnit tests over the pure geometry, run as part of `build`
(`src/test/java/.../domain/SphereShapeTest.java`):

- the whole build writes 44,692 distinct positions and **never the same one twice**
- the wall written is exactly the one-block band, nothing more and nothing less
- the outward phase lays the floor disc exactly once
- the caster's own column is never filled, but the floor beneath it is
- the particle sample lies on the wall, respects its budget, and covers all four quadrants

Two of these caught real defects the moment they were written: the floor block under the
caster was being written once per ring (29 redundant writes at radius 30), and the particle
sample's stride was rounded down so it overshot its own budget.

The geometry lives in `SphereShape`, deliberately free of Minecraft types, precisely so this
is testable without a game runtime.

---

## Layout

```
src/main/java/com/dsh/domainexpansion/
  DomainConfig.java          radius, duration and other tuning
  DomainExpansion.java       mod entry point
  block/                     the three domain blocks
  domain/SphereShape.java    pure build geometry (unit tested)
  entity/DomainEntity.java   the build, barrier, support spells and restore
  handler/DomainTickHandler  drives every open domain
  handler/DomainHitHandler   the forced-hit damage broadcast
  registry/                  block, entity and spell registration
  spell/                     the spell itself
src/test/java/.../domain/    geometry tests
src/main/resources/
  META-INF/mods.toml         dependencies on forge, irons_spellbooks, traveloptics
  assets/domain_expansion/   lang (zh_cn, en_us), spell icon, block models/states/textures
dist/                        the built jar, kept so a rollback can restore a working
                             artifact without rebuilding
```

---

## Diagnostics worth knowing about

The mod logs enough to tell a working domain from a broken one without a screenshot:

```
[DomainExpansion] domain opened at ... radius=30.0 captured=N
[DomainExpansion] outward phase done at t=Ns
[DomainExpansion] sphere complete at t=Ns, N written; 120s real time start now
[DomainExpansion] t=Ns phase=OUTWARD|VERTICAL|COMPLETE r=R y=Y written=N captured=N
[DomainExpansion] overflow active at t=Ns radius=R level=L
[DomainExpansion] rainfall active at t=Ns radius=R level=L
[DomainExpansion] domain closing after Ns of real time
[DomainExpansion] restoring N blocks over ~M ticks
[DomainExpansion] restored N blocks
[DomainExpansion] hit-rule: hit <target> for N -> broadcast: A entities in range, K damaged, D already dead, O outside the sphere
```

If the sphere is ever invisible again, the fastest way to split "block problem" from "build
problem" is one command in game:

```
/setblock ~ ~1 ~ domain_expansion:domain_shell
```

Unknown block means a registration problem, nothing appearing means a resource/render
problem, and a blue glowing cube means the block is fine and the build logic is at fault.

