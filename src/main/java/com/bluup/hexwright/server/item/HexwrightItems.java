package com.bluup.hexwright.server.item;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonGroups;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

public final class HexwrightItems {
    public static final Item CONFIGURABLE_STAFF = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("configurable_staff"),
        new ConfigurableStaffItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final Item HARMONIZED_PENTABOX = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("harmonized_pentabox"),
        new PentaboxItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );

    public static final Item HEXICON = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("hexicon"),
        new HexiconItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final Item FIELD_JOURNAL = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("field_journal"),
        new JournalItem(new Item.Properties().stacksTo(1))
    );

    public static final Item ENDLESS_POUCH = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("endless_pouch"),
        new EndlessPouchItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );

    public static final Item POCKET_CASTER = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("pocket_caster"),
        new PocketCasterItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final Item CRYSTALITE = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("crystalite"),
        new Item(new Item.Properties().rarity(Rarity.UNCOMMON))
    );

    public static final Item CRYSTALITE_NUGGET = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("crystalite_nugget"),
        new Item(new Item.Properties())
    );

    public static final Item RESONANT_KEY = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("resonant_key"),
        new com.bluup.hexwright.server.network.ResonantKeyItem(new Item.Properties().stacksTo(16))
    );

    public static final Item RESONANT_RING = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("resonant_ring"),
        new com.bluup.hexwright.server.network.ResonantRingItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final Item RING_OF_NEGATION = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("ring_of_negation"),
        new com.bluup.hexwright.server.combat.RingOfNegationItem(
            com.bluup.hexwright.server.combat.RingOfNegationItem.Kind.NEGATION,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final Item RING_OF_REPRISAL = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("ring_of_reprisal"),
        new com.bluup.hexwright.server.combat.RingOfNegationItem(
            com.bluup.hexwright.server.combat.RingOfNegationItem.Kind.REPRISAL,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    public static final Item RELIQUARY_SEAL = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("reliquary_seal"),
        new com.bluup.hexwright.server.reliquary.ReliquarySealItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );

    public static final Item WORLD_CRYSTAL = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("world_crystal"),
        new com.bluup.hexwright.server.portal.WorldCrystalItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    public static final Item GOLEM_POWER_ORB = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("golem_power_orb"),
        new com.bluup.hexwright.server.powerorb.PowerOrbItem(com.bluup.hexwright.server.powerorb.PowerOrbPower.GOLEM,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final Item SANCTUARY_POWER_ORB = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("sanctuary_power_orb"),
        new com.bluup.hexwright.server.powerorb.PowerOrbItem(com.bluup.hexwright.server.powerorb.PowerOrbPower.SANCTUARY,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final Item SEALED_SATCHEL = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("sealed_satchel"),
        new com.bluup.hexwright.server.reliquary.SatchelItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final Item MANTLE_OF_ASCENSION = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("mantle_of_ascension"),
        new com.bluup.hexwright.server.armour.MantleOfAscensionItem(
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant())
    );

    public static final Item HAT_OF_ASCENSION = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("hat_of_ascension"),
        new com.bluup.hexwright.server.armour.HatOfAscensionItem(
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant())
    );

    public static final Item CAPE_OF_PASSAGE = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("cape_of_passage"),
        new com.bluup.hexwright.server.armour.CapeOfPassageItem(
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant())
    );

    public static final Item HAT_OF_PASSAGE = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("hat_of_passage"),
        new com.bluup.hexwright.server.armour.HatOfPassageItem(
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant())
    );

    public static final Item TALISMAN = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("talisman"),
        new com.bluup.hexwright.server.talisman.TalismanItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final Item TALISMAN_OF_REPRIEVE = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("talisman_of_reprieve"),
        new com.bluup.hexwright.server.talisman.ReprieveTalismanItem(
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant())
    );

    public static final Item HEX_ENGRAVED_BOTTLE = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("hex_engraved_bottle"),
        new com.bluup.hexwright.server.remnant.HexEngravedBottleItem(
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );

    public static final Item ARTISAN_SIGNET = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("artisan_signet"),
        new com.bluup.hexwright.server.signet.ArtisanSignetItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );

    public static final Item WARDERS_SPECTACLES = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("warders_spectacles"),
        new com.bluup.hexwright.server.wardingbox.WardersSpectaclesItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );

    public static final Item FIELD_TUNER = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("field_tuner"),
        new FieldTunerItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );

    public static final Item BROOM = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("broom"),
        new com.bluup.hexwright.server.vehicle.BroomItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final Item CARPET = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("carpet"),
        new com.bluup.hexwright.server.vehicle.CarpetItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final Item VAULT_KEY = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("vault_key"),
        new com.bluup.hexwright.server.vault.VaultKeyItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final Item ANCHOR_SLATE = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("anchor_slate"),
        new AnchorSlateItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON))
    );

    public static final Item STONE_TABLET = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("stone_tablet"),
        new StoneTabletItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON))
    );

    public static final java.util.Map<com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality, Item>
        BATTLE_HAMMERS = registerBattleHammers();

    private static java.util.Map<com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality, Item> registerBattleHammers() {
        var hammers = new java.util.EnumMap<com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality, Item>(
            com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.class);
        for (var quality : com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.values()) {
            hammers.put(quality, Registry.register(
                BuiltInRegistries.ITEM,
                Hexwright.id(com.bluup.hexwright.server.weapon.BattleHammerGrade.itemId(quality)),
                new com.bluup.hexwright.server.weapon.BattleHammerItem(
                    quality,
                    com.bluup.hexwright.server.weapon.BattleHammerGrade.properties(quality))
            ));
        }
        return java.util.Collections.unmodifiableMap(hammers);
    }

    public static final java.util.Map<com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality, Item>
        BATTLE_AXES = registerBattleAxes();

    private static java.util.Map<com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality, Item> registerBattleAxes() {
        var axes = new java.util.EnumMap<com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality, Item>(
            com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.class);
        for (var quality : com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.values()) {
            axes.put(quality, Registry.register(
                BuiltInRegistries.ITEM,
                Hexwright.id(com.bluup.hexwright.server.weapon.BattleAxeGrade.itemId(quality)),
                new com.bluup.hexwright.server.weapon.BattleAxeItem(
                    quality,
                    com.bluup.hexwright.server.weapon.BattleAxeGrade.properties(quality))
            ));
        }
        return java.util.Collections.unmodifiableMap(axes);
    }

    public static final Item ETERNAL_WAR_HAMMER = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("eternal_war_hammer"),
        new com.bluup.hexwright.server.weapon.EternalHammerItem(
            7, -3.1F, artifactProperties())
    );

    public static final Item ETERNAL_BATTLE_AXE = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("eternal_battle_axe"),
        new com.bluup.hexwright.server.weapon.EternalBladeItem(
            6, -2.9F, artifactProperties())
    );

    public static final Item ETERNAL_GREAT_SWORD = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("eternal_great_sword"),
        new com.bluup.hexwright.server.weapon.EternalBladeItem(
            6, -2.9F, artifactProperties())
    );

    public static final Item ETERNAL_ODACHI = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("eternal_odachi"),
        new com.bluup.hexwright.server.weapon.EternalBladeItem(
            5, -2.7F, artifactProperties())
    );

    public static final Item ETERNAL_WAKIZASHI = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("eternal_wakizashi"),
        new com.bluup.hexwright.server.weapon.EternalWakizashiItem(
            4, -2.4F, artifactProperties())
    );

    public static final Item ETERNAL_BOW = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("eternal_bow"),
        new com.bluup.hexwright.server.weapon.EternalBowItem(artifactProperties())
    );

    public static final Item STARFALL_BOW = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("starfall_bow"),
        new com.bluup.hexwright.server.weapon.StarfallBowItem(artifactProperties())
    );

    public static final Item ETERNAL_SCYTHE = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("eternal_scythe"),
        new com.bluup.hexwright.server.weapon.EternalScytheItem(
            5, -2.8F, artifactProperties())
    );

    public static final java.util.List<Item> ARTIFACT_WEAPONS = java.util.List.of(
        ETERNAL_WAR_HAMMER, ETERNAL_BATTLE_AXE, ETERNAL_GREAT_SWORD, ETERNAL_ODACHI,
        ETERNAL_SCYTHE, ETERNAL_WAKIZASHI, ETERNAL_BOW, STARFALL_BOW);

    private static Item.Properties artifactProperties() {
        return new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
    }

    public static final java.util.Map<com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality, Item>
        ARCHER_GREAT_BOWS = registerGreatBows();

    private static java.util.Map<com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality, Item> registerGreatBows() {
        var bows = new java.util.EnumMap<com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality, Item>(
            com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.class);
        for (var quality : com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.values()) {
            bows.put(quality, Registry.register(
                BuiltInRegistries.ITEM,
                Hexwright.id(com.bluup.hexwright.server.weapon.GreatBowGrade.itemId(quality)),
                new com.bluup.hexwright.server.weapon.GreatBowItem(
                    quality,
                    com.bluup.hexwright.server.weapon.GreatBowGrade.properties(quality))
            ));
        }
        return java.util.Collections.unmodifiableMap(bows);
    }

    public static final java.util.Map<com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality, Item>
        DUELIST_SHORT_SWORDS = registerShortSwords();

    private static java.util.Map<com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality, Item> registerShortSwords() {
        var swords = new java.util.EnumMap<com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality, Item>(
            com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.class);
        for (var quality : com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.values()) {
            swords.put(quality, Registry.register(
                BuiltInRegistries.ITEM,
                Hexwright.id(com.bluup.hexwright.server.weapon.ShortSwordGrade.itemId(quality)),
                new com.bluup.hexwright.server.weapon.ShortSwordItem(
                    quality,
                    com.bluup.hexwright.server.weapon.ShortSwordGrade.properties(quality))
            ));
        }
        return java.util.Collections.unmodifiableMap(swords);
    }

    public static final Item AMETHYST_CORE = registerCore(com.bluup.hexwright.server.staff_assembly.StaffCoreItem.Kind.AMETHYST);
    public static final Item QUARTZ_CORE = registerCore(com.bluup.hexwright.server.staff_assembly.StaffCoreItem.Kind.AREA);
    public static final Item SCRIBE_CORE = registerCore(com.bluup.hexwright.server.staff_assembly.StaffCoreItem.Kind.SCRIBE);
    public static final Item TRAVELLER_CORE = registerCore(com.bluup.hexwright.server.staff_assembly.StaffCoreItem.Kind.TRAVELLER);
    public static final Item ECHO_CORE = registerCore(com.bluup.hexwright.server.staff_assembly.StaffCoreItem.Kind.BEAM);

    public static final Item WITHER_STORM_SPAWN_EGG = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("wither_storm_spawn_egg"),
        new SpawnEggItem(com.bluup.hexwright.server.boss.HexwrightBossEntities.WITHER_STORM,
            0x1B1226, 0xB000FF, new Item.Properties().rarity(Rarity.EPIC))
    );

    public static final Item COG_SENTINEL_SPAWN_EGG = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("cog_sentinel_spawn_egg"),
        new SpawnEggItem(com.bluup.hexwright.server.boss.HexwrightBossEntities.COG_SENTINEL,
            0x2A3440, 0x51E0FF, new Item.Properties().rarity(Rarity.EPIC))
    );

    public static final Item CORRUPT_EXPERIMENT_SPAWN_EGG = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("corrupt_experiment_spawn_egg"),
        new SpawnEggItem(com.bluup.hexwright.server.boss.HexwrightBossEntities.CORRUPT_EXPERIMENT,
            0x2B1B3A, 0x9B34EB, new Item.Properties().rarity(Rarity.EPIC))
    );

    public static final Item EXPERIMENTAL_CONSTRUCT_SPAWN_EGG = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("experimental_construct_spawn_egg"),
        new SpawnEggItem(com.bluup.hexwright.server.mob.HexwrightMobEntities.EXPERIMENTAL_CONSTRUCT,
            0x3E5068, 0x7FF3FF, new Item.Properties())
    );

    public static final Item SERVITOR_CONSTRUCT_SPAWN_EGG = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("servitor_construct_spawn_egg"),
        new SpawnEggItem(com.bluup.hexwright.server.mob.HexwrightMobEntities.SERVITOR_CONSTRUCT,
            0x1F2A3C, 0x9FD8FF, new Item.Properties())
    );

    public static final Item FRACTURED_CONSTRUCT_SPAWN_EGG = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("fractured_construct_spawn_egg"),
        new SpawnEggItem(com.bluup.hexwright.server.mob.HexwrightMobEntities.FRACTURED_CONSTRUCT,
            0x1F2A3C, 0x5A6B82, new Item.Properties())
    );

    public static final Item RUNESTONE_TITAN_SPAWN_EGG = Registry.register(
        BuiltInRegistries.ITEM,
        Hexwright.id("runestone_titan_spawn_egg"),
        new SpawnEggItem(com.bluup.hexwright.server.mob.HexwrightMobEntities.RUNESTONE_TITAN,
            0x2A3342, 0x4FE0FF, new Item.Properties())
    );

    private static Item registerCore(com.bluup.hexwright.server.staff_assembly.StaffCoreItem.Kind kind) {
        return Registry.register(
            BuiltInRegistries.ITEM,
            Hexwright.id(kind.id()),
            new com.bluup.hexwright.server.staff_assembly.StaffCoreItem(kind, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
        );
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
            entries.accept(HAT_OF_ASCENSION);
            entries.accept(MANTLE_OF_ASCENSION);
            entries.accept(HAT_OF_PASSAGE);
            entries.accept(CAPE_OF_PASSAGE);
            for (Item hammer : BATTLE_HAMMERS.values()) {
                entries.accept(hammer);
            }
            for (Item axe : BATTLE_AXES.values()) {
                entries.accept(axe);
            }
            for (Item weapon : ARTIFACT_WEAPONS) {
                entries.accept(weapon);
            }
            for (Item bow : ARCHER_GREAT_BOWS.values()) {
                entries.accept(bow);
            }
            for (Item sword : DUELIST_SHORT_SWORDS.values()) {
                entries.accept(sword);
            }
        });

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            entries.accept(CONFIGURABLE_STAFF);
            entries.accept(HARMONIZED_PENTABOX);
            entries.accept(FIELD_JOURNAL);
            entries.accept(HEXICON);
            entries.accept(ENDLESS_POUCH);
            entries.accept(POCKET_CASTER);
            entries.accept(CRYSTALITE);
            entries.accept(CRYSTALITE_NUGGET);
            entries.accept(RESONANT_KEY);
            entries.accept(RESONANT_RING);
            entries.accept(RING_OF_NEGATION);
            entries.accept(RING_OF_REPRISAL);
            entries.accept(RELIQUARY_SEAL);
            entries.accept(SEALED_SATCHEL);
            entries.accept(ARTISAN_SIGNET);
            entries.accept(WARDERS_SPECTACLES);
            entries.accept(WORLD_CRYSTAL);
            entries.accept(GOLEM_POWER_ORB);
            entries.accept(SANCTUARY_POWER_ORB);
            entries.accept(FIELD_TUNER);
            entries.accept(VAULT_KEY);
            for (int rune = 0; rune < DungeonGroups.LETTERS.length(); rune++) {
                entries.accept(AnchorSlateItem.of(String.valueOf(DungeonGroups.LETTERS.charAt(rune))));
            }
            entries.accept(BROOM);
            for (var variant : com.bluup.hexwright.server.vehicle.BroomVariant.values()) {
                if (variant == com.bluup.hexwright.server.vehicle.BroomVariant.ETHEREAL) {
                    continue;
                }
                entries.accept(variant.isArtifact()
                    ? variant.createStack()
                    : variant.createStack(
                        com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.FINE));
            }
            entries.accept(CARPET);
            for (var variant : com.bluup.hexwright.server.vehicle.CarpetVariant.values()) {
                if (variant != com.bluup.hexwright.server.vehicle.CarpetVariant.PURPLE) {
                    entries.accept(variant.createStack(
                        com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.FINE));
                }
            }
            entries.accept(com.bluup.hexwright.server.staff_assembly.StaffCoreData.create(AMETHYST_CORE,
                com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.FINE));
            entries.accept(com.bluup.hexwright.server.staff_assembly.StaffCoreData.create(QUARTZ_CORE,
                com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.FINE));
            entries.accept(com.bluup.hexwright.server.staff_assembly.StaffCoreData.create(SCRIBE_CORE,
                com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.FINE));
            entries.accept(com.bluup.hexwright.server.staff_assembly.StaffCoreData.create(TRAVELLER_CORE,
                com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.FINE));
            entries.accept(com.bluup.hexwright.server.staff_assembly.StaffCoreData.create(ECHO_CORE,
                com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.FINE));
            var showcaseTalisman = com.bluup.hexwright.server.talisman.TalismanData.create(
                com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.FINE);
            com.bluup.hexwright.server.talisman.TalismanData.setTrigger(showcaseTalisman,
                com.bluup.hexwright.server.talisman.TalismanData.Trigger.USE);
            com.bluup.hexwright.server.talisman.TalismanData.setContext(showcaseTalisman,
                com.bluup.hexwright.server.talisman.TalismanData.Context.GAZE);
            entries.accept(showcaseTalisman);
            entries.accept(TALISMAN_OF_REPRIEVE);
            for (var quality : com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality.values()) {
                entries.accept(com.bluup.hexwright.server.remnant.BottleData.create(quality));
            }
            entries.accept(STONE_TABLET);
            for (String recipe : com.bluup.hexwright.server.progression.RecipeTablets.get().gatedRecipes()) {
                entries.accept(StoneTabletItem.of(recipe));
            }
        });

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> {
            entries.accept(EXPERIMENTAL_CONSTRUCT_SPAWN_EGG);
            entries.accept(SERVITOR_CONSTRUCT_SPAWN_EGG);
            entries.accept(FRACTURED_CONSTRUCT_SPAWN_EGG);
            entries.accept(RUNESTONE_TITAN_SPAWN_EGG);
            entries.accept(WITHER_STORM_SPAWN_EGG);
            entries.accept(COG_SENTINEL_SPAWN_EGG);
            entries.accept(CORRUPT_EXPERIMENT_SPAWN_EGG);
        });
    }

    private HexwrightItems() {
    }
}
