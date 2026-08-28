package com.bluup.hexwright.server.block

import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory
import com.bluup.hexwright.server.armour.ArmourGemData
import com.bluup.hexwright.server.armour.ArmourSet
import com.bluup.hexwright.server.armour.HexwrightArmour
import com.bluup.hexwright.server.item.HexwrightItems
import com.bluup.hexwright.server.pentabox.PentaboxData
import com.bluup.hexwright.server.pocketcaster.PocketCasterData
import com.bluup.hexwright.server.staff_assembly.StaffCoreData
import com.bluup.hexwright.server.talisman.TalismanData
import com.bluup.hexwright.server.progression.RecipeTablets
import com.bluup.hexwright.server.vault.VaultKeyItem
import com.bluup.hexwright.server.vehicle.BroomVariant
import com.bluup.hexwright.server.vehicle.CarpetVariant
import com.bluup.hexwright.server.wardingbox.WardingBoxData
import net.minecraft.world.item.ItemStack

object WorktableRecipes {

    data class InfusionStep(
        val aspect: IngredientCategory,
        val amount: Double,
        val periodTicks: Int,
        val zoneHalf: Float
    )

    val POCKET_CASTER_STEPS = listOf(
        InfusionStep(IngredientCategory.CRYSTAL, 30.0, 64, 0.13f),
        InfusionStep(IngredientCategory.ARCANE, 24.0, 54, 0.11f),
        InfusionStep(IngredientCategory.SPATIAL, 18.0, 44, 0.09f),
        InfusionStep(IngredientCategory.METALLIC, 18.0, 36, 0.075f)
    )

    val TALISMAN_STEPS = listOf(
        InfusionStep(IngredientCategory.THREAD, 22.0, 64, 0.13f),
        InfusionStep(IngredientCategory.ORGANIC, 20.0, 54, 0.11f),
        InfusionStep(IngredientCategory.ARCANE, 24.0, 44, 0.09f),
        InfusionStep(IngredientCategory.ECHO, 16.0, 36, 0.075f)
    )

    val WARDING_BOX_STEPS = listOf(
        InfusionStep(IngredientCategory.CRYSTAL, 26.0, 64, 0.13f),
        InfusionStep(IngredientCategory.SPATIAL, 28.0, 52, 0.11f),
        InfusionStep(IngredientCategory.RADIANT, 16.0, 44, 0.09f),
        InfusionStep(IngredientCategory.ARCANE, 20.0, 36, 0.075f)
    )

    val PENTABOX_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 30.0, 64, 0.13f),
        InfusionStep(IngredientCategory.ECHO, 20.0, 54, 0.11f),
        InfusionStep(IngredientCategory.ARCANE, 22.0, 44, 0.09f),
        InfusionStep(IngredientCategory.CRYSTAL, 18.0, 36, 0.075f)
    )

    val AMETHYST_CORE_STEPS = listOf(
        InfusionStep(IngredientCategory.CRYSTAL, 20.0, 64, 0.13f),
        InfusionStep(IngredientCategory.ARCANE, 16.0, 54, 0.11f),
        InfusionStep(IngredientCategory.ENERGETIC, 14.0, 44, 0.09f),
        InfusionStep(IngredientCategory.METALLIC, 12.0, 36, 0.075f)
    )

    val QUARTZ_CORE_STEPS = listOf(
        InfusionStep(IngredientCategory.CRYSTAL, 26.0, 64, 0.13f),
        InfusionStep(IngredientCategory.SPATIAL, 24.0, 54, 0.11f),
        InfusionStep(IngredientCategory.RADIANT, 18.0, 44, 0.09f),
        InfusionStep(IngredientCategory.ARCANE, 18.0, 36, 0.075f)
    )

    val SCRIBE_CORE_STEPS = listOf(
        InfusionStep(IngredientCategory.ARCANE, 24.0, 64, 0.13f),
        InfusionStep(IngredientCategory.ECHO, 20.0, 54, 0.11f),
        InfusionStep(IngredientCategory.THREAD, 18.0, 44, 0.09f),
        InfusionStep(IngredientCategory.SPATIAL, 16.0, 36, 0.075f)
    )

    val TRAVELLER_CORE_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 28.0, 64, 0.13f),
        InfusionStep(IngredientCategory.ENERGETIC, 20.0, 54, 0.11f),
        InfusionStep(IngredientCategory.ARCANE, 18.0, 44, 0.09f),
        InfusionStep(IngredientCategory.ECHO, 16.0, 36, 0.075f)
    )

    val ECHO_CORE_STEPS = listOf(
        InfusionStep(IngredientCategory.ENERGETIC, 26.0, 64, 0.13f),
        InfusionStep(IngredientCategory.METALLIC, 20.0, 54, 0.11f),
        InfusionStep(IngredientCategory.RADIANT, 18.0, 44, 0.09f),
        InfusionStep(IngredientCategory.ARCANE, 16.0, 36, 0.075f)
    )

    val BROOM_ETHEREAL_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 24.0, 64, 0.13f),
        InfusionStep(IngredientCategory.ORGANIC, 20.0, 54, 0.11f),
        InfusionStep(IngredientCategory.ARCANE, 16.0, 44, 0.09f),
        InfusionStep(IngredientCategory.RADIANT, 14.0, 36, 0.075f)
    )

    val BROOM_STARRY_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 28.0, 64, 0.13f),
        InfusionStep(IngredientCategory.RADIANT, 26.0, 54, 0.11f),
        InfusionStep(IngredientCategory.ORGANIC, 22.0, 44, 0.09f),
        InfusionStep(IngredientCategory.ARCANE, 20.0, 36, 0.075f)
    )

    val BROOM_NOCTURNE_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 32.0, 60, 0.12f),
        InfusionStep(IngredientCategory.ARCANE, 28.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ECHO, 26.0, 42, 0.085f),
        InfusionStep(IngredientCategory.ORGANIC, 24.0, 34, 0.07f)
    )

    val BROOM_SWEET_ENCHANTRESS_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 34.0, 56, 0.115f),
        InfusionStep(IngredientCategory.ARCANE, 32.0, 48, 0.095f),
        InfusionStep(IngredientCategory.PRESTIGE, 30.0, 40, 0.08f),
        InfusionStep(IngredientCategory.THREAD, 28.0, 32, 0.065f)
    )

    val CARPET_PURPLE_STEPS = listOf(
        InfusionStep(IngredientCategory.THREAD, 26.0, 64, 0.13f),
        InfusionStep(IngredientCategory.ORGANIC, 18.0, 54, 0.11f),
        InfusionStep(IngredientCategory.SPATIAL, 20.0, 44, 0.09f),
        InfusionStep(IngredientCategory.ARCANE, 16.0, 36, 0.075f)
    )

    val CARPET_RED_STEPS = listOf(
        InfusionStep(IngredientCategory.THREAD, 30.0, 60, 0.12f),
        InfusionStep(IngredientCategory.SPATIAL, 26.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ORGANIC, 24.0, 42, 0.085f),
        InfusionStep(IngredientCategory.ARCANE, 22.0, 34, 0.07f)
    )

    val CARPET_TEAL_STEPS = listOf(
        InfusionStep(IngredientCategory.THREAD, 34.0, 56, 0.115f),
        InfusionStep(IngredientCategory.SPATIAL, 30.0, 48, 0.095f),
        InfusionStep(IngredientCategory.PRESTIGE, 28.0, 40, 0.08f),
        InfusionStep(IngredientCategory.RADIANT, 26.0, 32, 0.065f)
    )

    val ARTISAN_SIGNET_STEPS = listOf(
        InfusionStep(IngredientCategory.ARCANE, 20.0, 64, 0.13f),
        InfusionStep(IngredientCategory.METALLIC, 16.0, 54, 0.11f),
        InfusionStep(IngredientCategory.CRYSTAL, 14.0, 44, 0.09f),
        InfusionStep(IngredientCategory.RADIANT, 12.0, 36, 0.075f)
    )

    val RESONANT_KEY_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 24.0, 60, 0.12f),
        InfusionStep(IngredientCategory.ARCANE, 18.0, 50, 0.10f),
        InfusionStep(IngredientCategory.METALLIC, 14.0, 42, 0.085f),
        InfusionStep(IngredientCategory.ECHO, 14.0, 34, 0.07f)
    )

    val RESONANT_RING_STEPS = listOf(
        InfusionStep(IngredientCategory.ECHO, 26.0, 60, 0.12f),
        InfusionStep(IngredientCategory.SPATIAL, 22.0, 50, 0.10f),
        InfusionStep(IngredientCategory.METALLIC, 18.0, 42, 0.085f),
        InfusionStep(IngredientCategory.CRYSTAL, 16.0, 34, 0.07f)
    )

    val FIELD_MARKER_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 22.0, 60, 0.12f),
        InfusionStep(IngredientCategory.CRYSTAL, 18.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 16.0, 42, 0.085f),
        InfusionStep(IngredientCategory.METALLIC, 12.0, 34, 0.07f)
    )

    val FIELD_TUNER_STEPS = listOf(
        InfusionStep(IngredientCategory.ECHO, 22.0, 60, 0.12f),
        InfusionStep(IngredientCategory.SPATIAL, 20.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 16.0, 42, 0.085f),
        InfusionStep(IngredientCategory.RADIANT, 12.0, 34, 0.07f)
    )

    val RESONANT_ANCHOR_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 30.0, 56, 0.115f),
        InfusionStep(IngredientCategory.CRYSTAL, 24.0, 48, 0.095f),
        InfusionStep(IngredientCategory.ARCANE, 20.0, 40, 0.08f),
        InfusionStep(IngredientCategory.ECHO, 18.0, 32, 0.065f)
    )

    val VAULT_KEY_STEPS = listOf(
        InfusionStep(IngredientCategory.METALLIC, 20.0, 60, 0.12f),
        InfusionStep(IngredientCategory.SPATIAL, 18.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 14.0, 42, 0.085f),
        InfusionStep(IngredientCategory.ECHO, 12.0, 34, 0.07f)
    )

    val WARDERS_SPECTACLES_STEPS = listOf(
        InfusionStep(IngredientCategory.CRYSTAL, 22.0, 60, 0.12f),
        InfusionStep(IngredientCategory.RADIANT, 18.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 16.0, 42, 0.085f),
        InfusionStep(IngredientCategory.METALLIC, 12.0, 34, 0.07f)
    )

    val RELIQUARY_SEAL_STEPS = listOf(
        InfusionStep(IngredientCategory.ECHO, 24.0, 60, 0.12f),
        InfusionStep(IngredientCategory.SPATIAL, 20.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 18.0, 42, 0.085f),
        InfusionStep(IngredientCategory.CRYSTAL, 14.0, 34, 0.07f)
    )

    val SEALED_SATCHEL_STEPS = listOf(
        InfusionStep(IngredientCategory.THREAD, 24.0, 64, 0.13f),
        InfusionStep(IngredientCategory.LEATHER, 20.0, 54, 0.11f),
        InfusionStep(IngredientCategory.SPATIAL, 22.0, 44, 0.09f),
        InfusionStep(IngredientCategory.ARCANE, 18.0, 36, 0.075f)
    )

    val VEILWALKER_GEM_STEPS = listOf(
        InfusionStep(IngredientCategory.ECHO, 30.0, 56, 0.115f),
        InfusionStep(IngredientCategory.THREAD, 26.0, 48, 0.095f),
        InfusionStep(IngredientCategory.ARCANE, 24.0, 40, 0.08f),
        InfusionStep(IngredientCategory.SPATIAL, 22.0, 32, 0.065f)
    )

    val CANTOR_GEM_STEPS = listOf(
        InfusionStep(IngredientCategory.RADIANT, 30.0, 56, 0.115f),
        InfusionStep(IngredientCategory.THREAD, 26.0, 48, 0.095f),
        InfusionStep(IngredientCategory.ORGANIC, 24.0, 40, 0.08f),
        InfusionStep(IngredientCategory.ARCANE, 22.0, 32, 0.065f)
    )

    val HEXWARDEN_GEM_STEPS = listOf(
        InfusionStep(IngredientCategory.METALLIC, 30.0, 56, 0.115f),
        InfusionStep(IngredientCategory.CRYSTAL, 26.0, 48, 0.095f),
        InfusionStep(IngredientCategory.RADIANT, 24.0, 40, 0.08f),
        InfusionStep(IngredientCategory.ARCANE, 22.0, 32, 0.065f)
    )

    val AUGUR_GEM_STEPS = listOf(
        InfusionStep(IngredientCategory.ARCANE, 30.0, 56, 0.115f),
        InfusionStep(IngredientCategory.CRYSTAL, 26.0, 48, 0.095f),
        InfusionStep(IngredientCategory.ECHO, 24.0, 40, 0.08f),
        InfusionStep(IngredientCategory.RADIANT, 22.0, 32, 0.065f)
    )

    val VENATOR_CRYSTAL_STEPS = listOf(
        InfusionStep(IngredientCategory.FLEXIBLE, 30.0, 56, 0.115f),
        InfusionStep(IngredientCategory.THREAD, 26.0, 48, 0.095f),
        InfusionStep(IngredientCategory.CRYSTAL, 24.0, 40, 0.08f),
        InfusionStep(IngredientCategory.ENERGETIC, 22.0, 32, 0.065f)
    )

    val DOMITOR_GEM_STEPS = listOf(
        InfusionStep(IngredientCategory.FIRE, 30.0, 56, 0.115f),
        InfusionStep(IngredientCategory.NETHER, 26.0, 48, 0.095f),
        InfusionStep(IngredientCategory.METALLIC, 24.0, 40, 0.08f),
        InfusionStep(IngredientCategory.CRYSTAL, 22.0, 32, 0.065f)
    )

    val BATTLE_AXE_STEPS = listOf(
        InfusionStep(IngredientCategory.METALLIC, 26.0, 64, 0.13f),
        InfusionStep(IngredientCategory.ENERGETIC, 20.0, 54, 0.11f),
        InfusionStep(IngredientCategory.FLEXIBLE, 16.0, 44, 0.09f),
        InfusionStep(IngredientCategory.RADIANT, 14.0, 36, 0.075f)
    )

    val BATTLE_HAMMER_STEPS = listOf(
        InfusionStep(IngredientCategory.METALLIC, 30.0, 64, 0.13f),
        InfusionStep(IngredientCategory.ENERGETIC, 22.0, 54, 0.11f),
        InfusionStep(IngredientCategory.FIRE, 18.0, 44, 0.09f),
        InfusionStep(IngredientCategory.RADIANT, 14.0, 36, 0.075f)
    )

    val DUELIST_SHORT_SWORD_STEPS = listOf(
        InfusionStep(IngredientCategory.METALLIC, 20.0, 60, 0.12f),
        InfusionStep(IngredientCategory.FLEXIBLE, 18.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ENERGETIC, 14.0, 42, 0.085f),
        InfusionStep(IngredientCategory.THREAD, 12.0, 34, 0.07f)
    )

    val ARCHER_GREAT_BOW_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 24.0, 60, 0.12f),
        InfusionStep(IngredientCategory.ENERGETIC, 22.0, 50, 0.10f),
        InfusionStep(IngredientCategory.METALLIC, 16.0, 42, 0.085f),
        InfusionStep(IngredientCategory.ARCANE, 14.0, 34, 0.07f)
    )

    val HEX_ENGRAVED_BOTTLE_STEPS = listOf(
        InfusionStep(IngredientCategory.CRYSTAL, 22.0, 60, 0.12f),
        InfusionStep(IngredientCategory.ARCANE, 20.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ORGANIC, 16.0, 42, 0.085f),
        InfusionStep(IngredientCategory.ECHO, 14.0, 34, 0.07f)
    )

    data class WorktableRecipe(
        val nameKey: String,
        val steps: List<InfusionStep>,
        val requiredMastery: PocketCasterData.Quality?,
        val graded: Boolean = true,
        val assemble: (PocketCasterData.Quality) -> ItemStack
    ) {
        fun preview(): ItemStack = assemble(PocketCasterData.Quality.SOUND)
    }

    val RECIPES = listOf(
        WorktableRecipe(
            "item.hexwright.pocket_caster",
            POCKET_CASTER_STEPS,
            requiredMastery = null
        ) { quality -> PocketCasterData.create(quality) },
        WorktableRecipe(
            "block.hexwright.warding_box",
            WARDING_BOX_STEPS,
            requiredMastery = PocketCasterData.Quality.FINE
        ) { quality -> WardingBoxData.create(quality) },
        WorktableRecipe(
            "item.hexwright.talisman",
            TALISMAN_STEPS,
            requiredMastery = PocketCasterData.Quality.SOUND
        ) { quality -> TalismanData.create(quality) },
        WorktableRecipe(
            "item.hexwright.hex_engraved_bottle",
            HEX_ENGRAVED_BOTTLE_STEPS,
            requiredMastery = PocketCasterData.Quality.SOUND
        ) { quality -> com.bluup.hexwright.server.remnant.BottleData.create(quality) },
        WorktableRecipe(
            "item.hexwright.harmonized_pentabox",
            PENTABOX_STEPS,
            requiredMastery = PocketCasterData.Quality.FINE
        ) { quality -> PentaboxData.create(quality) },
        WorktableRecipe(
            "item.hexwright.amethyst_core",
            AMETHYST_CORE_STEPS,
            requiredMastery = null
        ) { quality -> StaffCoreData.create(HexwrightItems.AMETHYST_CORE, quality) },
        WorktableRecipe(
            "item.hexwright.quartz_core",
            QUARTZ_CORE_STEPS,
            requiredMastery = null
        ) { quality -> StaffCoreData.create(HexwrightItems.QUARTZ_CORE, quality) },
        WorktableRecipe(
            "item.hexwright.scribe_core",
            SCRIBE_CORE_STEPS,
            requiredMastery = null
        ) { quality -> StaffCoreData.create(HexwrightItems.SCRIBE_CORE, quality) },
        WorktableRecipe(
            "item.hexwright.traveller_core",
            TRAVELLER_CORE_STEPS,
            requiredMastery = null
        ) { quality -> StaffCoreData.create(HexwrightItems.TRAVELLER_CORE, quality) },
        WorktableRecipe(
            "item.hexwright.echo_core",
            ECHO_CORE_STEPS,
            requiredMastery = null
        ) { quality -> StaffCoreData.create(HexwrightItems.ECHO_CORE, quality) },
        WorktableRecipe(
            BroomVariant.ETHEREAL.nameKey(),
            BROOM_ETHEREAL_STEPS,
            requiredMastery = BroomVariant.ETHEREAL.requiredMastery()
        ) { quality -> BroomVariant.ETHEREAL.createStack(quality) },
        WorktableRecipe(
            BroomVariant.STARRY.nameKey(),
            BROOM_STARRY_STEPS,
            requiredMastery = BroomVariant.STARRY.requiredMastery()
        ) { quality -> BroomVariant.STARRY.createStack(quality) },
        WorktableRecipe(
            BroomVariant.NOCTURNE.nameKey(),
            BROOM_NOCTURNE_STEPS,
            requiredMastery = BroomVariant.NOCTURNE.requiredMastery()
        ) { quality -> BroomVariant.NOCTURNE.createStack(quality) },
        WorktableRecipe(
            BroomVariant.SWEET_ENCHANTRESS.nameKey(),
            BROOM_SWEET_ENCHANTRESS_STEPS,
            requiredMastery = BroomVariant.SWEET_ENCHANTRESS.requiredMastery()
        ) { quality -> BroomVariant.SWEET_ENCHANTRESS.createStack(quality) },
        WorktableRecipe(
            CarpetVariant.PURPLE.nameKey(),
            CARPET_PURPLE_STEPS,
            requiredMastery = CarpetVariant.PURPLE.requiredMastery()
        ) { quality -> CarpetVariant.PURPLE.createStack(quality) },
        WorktableRecipe(
            CarpetVariant.RED.nameKey(),
            CARPET_RED_STEPS,
            requiredMastery = CarpetVariant.RED.requiredMastery()
        ) { quality -> CarpetVariant.RED.createStack(quality) },
        WorktableRecipe(
            CarpetVariant.TEAL.nameKey(),
            CARPET_TEAL_STEPS,
            requiredMastery = CarpetVariant.TEAL.requiredMastery()
        ) { quality -> CarpetVariant.TEAL.createStack(quality) },
        WorktableRecipe(
            ArmourSet.VEILWALKER.gemTitleKey(),
            VEILWALKER_GEM_STEPS,
            requiredMastery = PocketCasterData.Quality.SOUND
        ) { quality -> ArmourGemData.create(HexwrightArmour.gem(ArmourSet.VEILWALKER), quality) },
        WorktableRecipe(
            ArmourSet.CANTOR.gemTitleKey(),
            CANTOR_GEM_STEPS,
            requiredMastery = PocketCasterData.Quality.SOUND
        ) { quality -> ArmourGemData.create(HexwrightArmour.gem(ArmourSet.CANTOR), quality) },
        WorktableRecipe(
            ArmourSet.HEXWARDEN.gemTitleKey(),
            HEXWARDEN_GEM_STEPS,
            requiredMastery = PocketCasterData.Quality.SOUND
        ) { quality -> ArmourGemData.create(HexwrightArmour.gem(ArmourSet.HEXWARDEN), quality) },
        WorktableRecipe(
            ArmourSet.AUGUR.gemTitleKey(),
            AUGUR_GEM_STEPS,
            requiredMastery = PocketCasterData.Quality.SOUND
        ) { quality -> ArmourGemData.create(HexwrightArmour.gem(ArmourSet.AUGUR), quality) },
        WorktableRecipe(
            ArmourSet.VENATOR.gemTitleKey(),
            VENATOR_CRYSTAL_STEPS,
            requiredMastery = PocketCasterData.Quality.SOUND
        ) { quality -> ArmourGemData.create(HexwrightArmour.gem(ArmourSet.VENATOR), quality) },
        WorktableRecipe(
            ArmourSet.DOMITOR.gemTitleKey(),
            DOMITOR_GEM_STEPS,
            requiredMastery = PocketCasterData.Quality.SOUND
        ) { quality -> ArmourGemData.create(HexwrightArmour.gem(ArmourSet.DOMITOR), quality) },
        WorktableRecipe(
            "item.hexwright.battle_axe",
            BATTLE_AXE_STEPS,
            requiredMastery = PocketCasterData.Quality.FINE
        ) { quality -> ItemStack(HexwrightItems.BATTLE_AXES[quality]!!) },
        WorktableRecipe(
            "item.hexwright.battle_hammer",
            BATTLE_HAMMER_STEPS,
            requiredMastery = PocketCasterData.Quality.FINE
        ) { quality -> ItemStack(HexwrightItems.BATTLE_HAMMERS[quality]!!) },
        WorktableRecipe(
            "item.hexwright.duelist_short_sword",
            DUELIST_SHORT_SWORD_STEPS,
            requiredMastery = PocketCasterData.Quality.FINE
        ) { quality -> ItemStack(HexwrightItems.DUELIST_SHORT_SWORDS[quality]!!) },
        WorktableRecipe(
            "item.hexwright.archer_great_bow",
            ARCHER_GREAT_BOW_STEPS,
            requiredMastery = PocketCasterData.Quality.FINE
        ) { quality -> ItemStack(HexwrightItems.ARCHER_GREAT_BOWS[quality]!!) },
        WorktableRecipe(
            "item.hexwright.artisan_signet",
            ARTISAN_SIGNET_STEPS,
            requiredMastery = null,
            graded = false
        ) { ItemStack(HexwrightItems.ARTISAN_SIGNET) },
        WorktableRecipe(
            "item.hexwright.resonant_key",
            RESONANT_KEY_STEPS,
            requiredMastery = null,
            graded = false
        ) { ItemStack(HexwrightItems.RESONANT_KEY) },
        WorktableRecipe(
            "item.hexwright.resonant_ring",
            RESONANT_RING_STEPS,
            requiredMastery = PocketCasterData.Quality.FINE,
            graded = false
        ) { ItemStack(HexwrightItems.RESONANT_RING) },
        WorktableRecipe(
            "block.hexwright.field_marker",
            FIELD_MARKER_STEPS,
            requiredMastery = null,
            graded = false
        ) { ItemStack(HexwrightBlocks.FIELD_MARKER_ITEM) },
        WorktableRecipe(
            "item.hexwright.field_tuner",
            FIELD_TUNER_STEPS,
            requiredMastery = null,
            graded = false
        ) { ItemStack(HexwrightItems.FIELD_TUNER) },
        WorktableRecipe(
            "block.hexwright.resonant_anchor",
            RESONANT_ANCHOR_STEPS,
            requiredMastery = PocketCasterData.Quality.SOUND,
            graded = false
        ) { ItemStack(HexwrightBlocks.RESONANT_ANCHOR_ITEM) },
        WorktableRecipe(
            "item.hexwright.vault_key",
            VAULT_KEY_STEPS,
            requiredMastery = null
        ) { quality -> VaultKeyItem.blank(quality) },
        WorktableRecipe(
            "item.hexwright.warders_spectacles",
            WARDERS_SPECTACLES_STEPS,
            requiredMastery = null,
            graded = false
        ) { ItemStack(HexwrightItems.WARDERS_SPECTACLES) },
        WorktableRecipe(
            "item.hexwright.reliquary_seal",
            RELIQUARY_SEAL_STEPS,
            requiredMastery = null,
            graded = false
        ) { ItemStack(HexwrightItems.RELIQUARY_SEAL) },
        WorktableRecipe(
            "item.hexwright.sealed_satchel",
            SEALED_SATCHEL_STEPS,
            requiredMastery = PocketCasterData.Quality.FINE,
            graded = false
        ) { ItemStack(HexwrightItems.SEALED_SATCHEL) }
    )

    fun defaultRecipe(): Int =
        RECIPES.indices.firstOrNull { !RecipeTablets.requiresTablet(RECIPES[it].nameKey) } ?: 0
}
