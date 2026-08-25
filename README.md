# Farmhand

Farmer villagers replant the crop they just harvested, instead of the first seed they happen to carry.

[Modrinth](https://modrinth.com/mod/farmhand) · Fabric & NeoForge · Minecraft 1.20 – 26.2 · MIT

## The problem

Vanilla's `HarvestFarmland` behaviour walks the villager's inventory **in slot order** and plants
the first plantable seed it finds:

```java
for (int i = 0; i < inventory.getContainerSize(); i++) {
    ItemStack stack = inventory.getItem(i);
    if (!stack.isEmpty() && stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)
            && stack.getItem() instanceof BlockItem blockItem) {
        level.setBlockAndUpdate(pos, blockItem.getBlock().defaultBlockState());
        break;
    }
}
```

Nothing in there looks at what was harvested. A farmer that pulls up carrots will replant wheat
whenever wheat seeds sit in an earlier slot.

Farmhand remembers the harvested crop and makes the farmer plant it back — but only when the
matching seed is actually in the inventory. Otherwise it stays out of the way and vanilla applies,
so the plot never ends up empty because of this mod.

## What this mod does *not* claim

**Modded crops already work in vanilla.** Since 1.20 the game matches seeds through the
`villager_plantable_seeds` item tag plus a `BlockItem` check, so any mod that tags its seed is
supported without help. Farmhand only fixes *which* crop gets chosen.

It also does not touch search radius, cooldowns or walking speed — those stay vanilla.

## Installing

Server-side only. Villager AI runs on the logical server, so:

| Situation | Where it goes |
| --- | --- |
| Single player | your own `mods` folder — the integrated server runs it |
| Your own server | the **server's** `mods` folder; connecting players need nothing |
| Someone else's server | installing it client-side does nothing |

No dependencies, no configuration, no commands.

| Loader | Minecraft |
| --- | --- |
| Fabric | 1.20 – 26.2 |
| NeoForge | 1.20.2 – 26.2 |
| Quilt | same builds as Fabric |

Forge is not supported: it is a rump on modern versions and its audience moved to NeoForge.

## Building

```bash
./gradlew build
```

Produces one jar per version/loader pair under `versions/<version>-<loader>/build/libs/`.
For a release build without the `-SNAPSHOT` suffix, set `MOD_IS_RELEASE=true`.

Version-specific code is handled by [Stonecutter](https://stonecutter.kikugie.dev/) comment
preprocessing, so the whole matrix is built from a single source tree. Only three branches exist,
each tied to a concrete rename in the game:

| Branch | Reason |
| --- | --- |
| `>=1.21.5` | `VillagerData` switched to `Holder`s |
| `>=1.21.11` | `Villager` moved to `net.minecraft.world.entity.npc.villager` |
| `>=26.2` | entity type constants moved from `EntityType` to `EntityTypes` |

## Testing

```bash
bash scripts/smoke-test.sh              # every build in the matrix
bash scripts/smoke-test.sh 26.2-fabric  # a single build
```

The script boots a **real dedicated server** for each of the 17 builds and requires four things:
the self-test reached a verdict, no mixin injection failed, the loader actually loaded the mod,
and all functional scenarios passed. Servers bind port `25599` rather than the default, so a local
development server is never touched.

The functional check lives in `FarmhandSelfTest`, inert unless `-Dfarmhand.selftest=true` is set.
It runs two scenarios per crop — carrots, potatoes and beetroot:

1. farmer carrying wheat seeds **and** the right seed → must replant that crop;
2. farmer carrying only wheat seeds → must replant wheat, exactly like vanilla.

The second scenario is what makes the first meaningful: it pins down that vanilla plants wheat
here, so the difference in scenario 1 can only come from this mod.

Those six run against `HarvestFarmland` driven directly through an `@Invoker`, which keeps them
deterministic. A seventh scenario spawns a **live villager** and hands control back to the server —
the brain, the schedule and the plot search all participate, and the plot is checked 600 ticks
later. That one covers the path the fast scenarios deliberately skip.

GameTest is not used on purpose: Mojang rewrote that API in 1.21.5, and covering 1.20 – 26.2 would
need two independent implementations with structure templates and datapack definitions.

## License

MIT. The Gradle build is derived from
[stonecutter-mod-template](https://github.com/rotgruengelb/stonecutter-mod-template); see `LICENSE`.

---

# Farmhand — по-русски

Жители-фермеры пересаживают ту культуру, которую собрали, а не первое семя из инвентаря.

## В чём проблема

Ванильное поведение `HarvestFarmland` перебирает инвентарь жителя **по порядку слотов** и сажает
первое подходящее семя. В коде нет ни одной проверки того, что именно было собрано, поэтому
фермер, выкопавший морковь, засеет грядку пшеницей — просто потому что пшеничные семена лежали
в слоте раньше.

Мод запоминает собранную культуру и заставляет вернуть на грядку именно её — но только если
нужное семя у жителя действительно есть. Иначе он не вмешивается и работает ваниль, поэтому
грядка не останется пустой из-за мода.

**Модовые культуры ваниль поддерживает сама** начиная с 1.20: отбор идёт через тег
`villager_plantable_seeds` и проверку на `BlockItem`. Farmhand чинит только выбор культуры и не
трогает радиус поиска, задержки и скорость ходьбы.

## Установка

Только на сервер — ИИ жителей выполняется на логическом сервере.

| Ситуация | Куда класть |
| --- | --- |
| Одиночная игра | в свою папку `mods` — работу делает встроенный сервер |
| Свой сервер | в папку `mods` **сервера**; игрокам ставить ничего не нужно |
| Чужой сервер | ставить бесполезно, поведение решает сервер |

Без зависимостей, конфигов и команд. Fabric 1.20–26.2, NeoForge 1.20.2–26.2, Quilt на сборках
Fabric. Forge не поддерживается.

## Сборка и тесты

```bash
./gradlew build                         # все 17 сборок матрицы
bash scripts/smoke-test.sh              # прогон по всей матрице
bash scripts/smoke-test.sh 26.2-fabric  # одна сборка
```

Версионные различия закрыты препроцессором Stonecutter, поэтому вся матрица собирается из одного
исходника: три ветки, каждая под конкретное переименование в игре.

Smoke-тест поднимает **настоящий выделенный сервер** на каждой сборке и требует, чтобы инъекции
миксина применились, загрузчик подхватил мод и прошли восемь проверок: три культуры по два
сценария, живой житель и итог. Серверы слушают порт `25599`, а не дефолтный, чтобы не задеть
локальный сервер разработчика.
