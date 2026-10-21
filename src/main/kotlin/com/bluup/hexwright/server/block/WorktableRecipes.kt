package com.bluup.hexwright.server.block

import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory
import com.bluup.hexwright.server.armour.ArmourGemData
import com.bluup.hexwright.server.armour.ArmourSet
import com.bluup.hexwright.server.armour.HexwrightArmour
import com.bluup.hexwright.server.hexweave.HexLoom
import com.bluup.hexwright.server.item.ArtifactItem
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
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

object WorktableRecipes {

    private const val PIPES_PER_CRAFT = 8

    const val ARTIFACT_GOLEM_CORES = 2

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


    val BROOM_SPLINTERED_SWEEPER_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 22.0, 66, 0.135f),
        InfusionStep(IngredientCategory.ORGANIC, 18.0, 56, 0.115f),
        InfusionStep(IngredientCategory.METALLIC, 16.0, 46, 0.095f),
        InfusionStep(IngredientCategory.ARCANE, 12.0, 38, 0.08f)
    )

    val BROOM_BRISTLEBACK_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 26.0, 62, 0.125f),
        InfusionStep(IngredientCategory.ORGANIC, 24.0, 52, 0.105f),
        InfusionStep(IngredientCategory.METALLIC, 20.0, 44, 0.09f),
        InfusionStep(IngredientCategory.ARCANE, 18.0, 36, 0.075f)
    )

    val BROOM_CROOKED_COMBER_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 28.0, 62, 0.125f),
        InfusionStep(IngredientCategory.ORGANIC, 24.0, 52, 0.105f),
        InfusionStep(IngredientCategory.THREAD, 20.0, 44, 0.09f),
        InfusionStep(IngredientCategory.ARCANE, 18.0, 36, 0.075f)
    )

    val BROOM_WHISPERWIND_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 32.0, 58, 0.12f),
        InfusionStep(IngredientCategory.ARCANE, 28.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ORGANIC, 24.0, 42, 0.085f),
        InfusionStep(IngredientCategory.THREAD, 20.0, 34, 0.07f)
    )

    val BROOM_GLEAMGLIDE_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 36.0, 54, 0.11f),
        InfusionStep(IngredientCategory.RADIANT, 34.0, 46, 0.095f),
        InfusionStep(IngredientCategory.ARCANE, 30.0, 38, 0.078f),
        InfusionStep(IngredientCategory.PRESTIGE, 28.0, 30, 0.062f)
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

    val RING_OF_NEGATION_STEPS = listOf(
        InfusionStep(IngredientCategory.ARCANE, 28.0, 62, 0.13f),
        InfusionStep(IngredientCategory.CRYSTAL, 22.0, 52, 0.105f),
        InfusionStep(IngredientCategory.METALLIC, 18.0, 44, 0.09f),
        InfusionStep(IngredientCategory.PRESTIGE, 16.0, 34, 0.075f)
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

    val HEXID_TANK_STEPS = listOf(
        InfusionStep(IngredientCategory.CRYSTAL, 14.0, 60, 0.12f),
        InfusionStep(IngredientCategory.METALLIC, 12.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 10.0, 42, 0.085f),
        InfusionStep(IngredientCategory.SPATIAL, 6.0, 34, 0.07f)
    )

    val HEXID_PIPE_STEPS = listOf(
        InfusionStep(IngredientCategory.METALLIC, 12.0, 60, 0.12f),
        InfusionStep(IngredientCategory.CRYSTAL, 6.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 4.0, 42, 0.085f),
        InfusionStep(IngredientCategory.SPATIAL, 2.0, 34, 0.07f)
    )

    val ALEMBIX_STEPS = listOf(
        InfusionStep(IngredientCategory.ARCANE, 26.0, 60, 0.12f),
        InfusionStep(IngredientCategory.CRYSTAL, 22.0, 50, 0.10f),
        InfusionStep(IngredientCategory.SPATIAL, 18.0, 42, 0.085f),
        InfusionStep(IngredientCategory.METALLIC, 14.0, 34, 0.07f)
    )

    val LIQUEFACTRIUM_STEPS = listOf(
        InfusionStep(IngredientCategory.METALLIC, 18.0, 60, 0.12f),
        InfusionStep(IngredientCategory.CRYSTAL, 16.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 12.0, 42, 0.085f),
        InfusionStep(IngredientCategory.ECHO, 8.0, 34, 0.07f)
    )


    val ETERNAL_WAR_HAMMER_STEPS = listOf(
        InfusionStep(IngredientCategory.METALLIC, 70.0, 60, 0.12f),
        InfusionStep(IngredientCategory.ENERGETIC, 60.0, 50, 0.10f),
        InfusionStep(IngredientCategory.FIRE, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 48.0, 34, 0.07f)
    )

    val ETERNAL_BATTLE_AXE_STEPS = listOf(
        InfusionStep(IngredientCategory.METALLIC, 66.0, 60, 0.12f),
        InfusionStep(IngredientCategory.ENERGETIC, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.FLEXIBLE, 50.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 46.0, 34, 0.07f)
    )

    val ETERNAL_GREAT_SWORD_STEPS = listOf(
        InfusionStep(IngredientCategory.METALLIC, 68.0, 60, 0.12f),
        InfusionStep(IngredientCategory.RADIANT, 56.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ENERGETIC, 50.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 46.0, 34, 0.07f)
    )

    val ETERNAL_ODACHI_STEPS = listOf(
        InfusionStep(IngredientCategory.METALLIC, 62.0, 60, 0.12f),
        InfusionStep(IngredientCategory.FLEXIBLE, 56.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ENERGETIC, 50.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 44.0, 34, 0.07f)
    )

    val ETERNAL_SCYTHE_STEPS = listOf(
        InfusionStep(IngredientCategory.ECHO, 64.0, 60, 0.12f),
        InfusionStep(IngredientCategory.METALLIC, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ORGANIC, 50.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 48.0, 34, 0.07f)
    )

    val ETERNAL_WAKIZASHI_STEPS = listOf(
        InfusionStep(IngredientCategory.METALLIC, 60.0, 60, 0.12f),
        InfusionStep(IngredientCategory.ARCANE, 56.0, 50, 0.10f),
        InfusionStep(IngredientCategory.FLEXIBLE, 50.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 44.0, 34, 0.07f)
    )

    val FROSTBITE_STEPS = listOf(
        InfusionStep(IngredientCategory.CRYSTAL, 62.0, 60, 0.12f),
        InfusionStep(IngredientCategory.METALLIC, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.SPATIAL, 54.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 50.0, 34, 0.07f)
    )

    val KING_OF_PURGATORY_STEPS = listOf(
        InfusionStep(IngredientCategory.ENERGETIC, 64.0, 60, 0.12f),
        InfusionStep(IngredientCategory.SPATIAL, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.METALLIC, 54.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 50.0, 34, 0.07f)
    )

    val SILVER_STARLIGHT_STEPS = listOf(
        InfusionStep(IngredientCategory.CRYSTAL, 66.0, 60, 0.12f),
        InfusionStep(IngredientCategory.RADIANT, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.METALLIC, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 48.0, 34, 0.07f)
    )

    val ETERNAL_BOW_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 62.0, 60, 0.12f),
        InfusionStep(IngredientCategory.ENERGETIC, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.FLEXIBLE, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 46.0, 34, 0.07f)
    )

    val STARFALL_BOW_STEPS = listOf(
        InfusionStep(IngredientCategory.RADIANT, 66.0, 60, 0.12f),
        InfusionStep(IngredientCategory.SPATIAL, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ENERGETIC, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 48.0, 34, 0.07f)
    )

    val NECROMANCER_SWORD_STEPS = listOf(
        InfusionStep(IngredientCategory.ECHO, 64.0, 60, 0.12f),
        InfusionStep(IngredientCategory.METALLIC, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 48.0, 34, 0.07f)
    )

    val SPIDER_BOW_STEPS = listOf(
        InfusionStep(IngredientCategory.THREAD, 64.0, 60, 0.12f),
        InfusionStep(IngredientCategory.SPATIAL, 56.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ORGANIC, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 46.0, 34, 0.07f)
    )

    val FORGE_HAMMER_STEPS = listOf(
        InfusionStep(IngredientCategory.FIRE, 66.0, 60, 0.12f),
        InfusionStep(IngredientCategory.METALLIC, 60.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ENERGETIC, 50.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 46.0, 34, 0.07f)
    )

    val BROOM_STARBEAM_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 66.0, 60, 0.12f),
        InfusionStep(IngredientCategory.RADIANT, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 50.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 46.0, 34, 0.07f)
    )

    val BROOM_NIGHTSWEEP_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 66.0, 60, 0.12f),
        InfusionStep(IngredientCategory.ECHO, 56.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 46.0, 34, 0.07f)
    )

    val BROOM_TWIN_WHISK_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 70.0, 60, 0.12f),
        InfusionStep(IngredientCategory.THREAD, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ORGANIC, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 48.0, 34, 0.07f)
    )

    val RING_OF_REPRISAL_STEPS = listOf(
        InfusionStep(IngredientCategory.ARCANE, 64.0, 60, 0.12f),
        InfusionStep(IngredientCategory.METALLIC, 56.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ENERGETIC, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 48.0, 34, 0.07f)
    )

    val TALISMAN_OF_REPRIEVE_STEPS = listOf(
        InfusionStep(IngredientCategory.ORGANIC, 62.0, 60, 0.12f),
        InfusionStep(IngredientCategory.ARCANE, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ECHO, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 46.0, 34, 0.07f)
    )

    val GOLEM_POWER_ORB_STEPS = listOf(
        InfusionStep(IngredientCategory.METALLIC, 64.0, 60, 0.12f),
        InfusionStep(IngredientCategory.ARCANE, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.CRYSTAL, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.ENERGETIC, 46.0, 34, 0.07f)
    )

    val CAT_POWER_ORB_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 62.0, 60, 0.12f),
        InfusionStep(IngredientCategory.ORGANIC, 56.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.ENERGETIC, 46.0, 34, 0.07f)
    )

    val GUARDIAN_POWER_ORB_STEPS = listOf(
        InfusionStep(IngredientCategory.LEATHER, 64.0, 60, 0.12f),
        InfusionStep(IngredientCategory.METALLIC, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.CRYSTAL, 46.0, 34, 0.07f)
    )

    val SANCTUARY_POWER_ORB_STEPS = listOf(
        InfusionStep(IngredientCategory.ORGANIC, 64.0, 60, 0.12f),
        InfusionStep(IngredientCategory.RADIANT, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.LEATHER, 46.0, 34, 0.07f)
    )

    val TEETH_POWER_ORB_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 66.0, 60, 0.12f),
        InfusionStep(IngredientCategory.END, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.METALLIC, 44.0, 34, 0.07f)
    )

    val DUST_POWER_ORB_STEPS = listOf(
        InfusionStep(IngredientCategory.ARCANE, 64.0, 60, 0.12f),
        InfusionStep(IngredientCategory.FLEXIBLE, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ENERGETIC, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.CRYSTAL, 46.0, 34, 0.07f)
    )

    val MANTLE_OF_ASCENSION_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 66.0, 60, 0.12f),
        InfusionStep(IngredientCategory.THREAD, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 48.0, 34, 0.07f)
    )

    val HAT_OF_ASCENSION_STEPS = listOf(
        InfusionStep(IngredientCategory.THREAD, 50.0, 60, 0.12f),
        InfusionStep(IngredientCategory.ARCANE, 44.0, 50, 0.10f),
        InfusionStep(IngredientCategory.PRESTIGE, 40.0, 42, 0.085f),
        InfusionStep(IngredientCategory.SPATIAL, 36.0, 34, 0.07f)
    )

    val CAPE_OF_PASSAGE_STEPS = listOf(
        InfusionStep(IngredientCategory.SPATIAL, 66.0, 60, 0.12f),
        InfusionStep(IngredientCategory.END, 58.0, 50, 0.10f),
        InfusionStep(IngredientCategory.THREAD, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.ARCANE, 48.0, 34, 0.07f)
    )

    val HAT_OF_PASSAGE_STEPS = listOf(
        InfusionStep(IngredientCategory.THREAD, 50.0, 60, 0.12f),
        InfusionStep(IngredientCategory.END, 44.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ARCANE, 40.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 36.0, 34, 0.07f)
    )

    val ARTIFACT_VAULT_KEY_STEPS = listOf(
        InfusionStep(IngredientCategory.METALLIC, 64.0, 60, 0.12f),
        InfusionStep(IngredientCategory.SPATIAL, 60.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ECHO, 52.0, 42, 0.085f),
        InfusionStep(IngredientCategory.PRESTIGE, 48.0, 34, 0.07f)
    )


    val HEX_LOOM_STEPS = listOf(
        InfusionStep(IngredientCategory.ECHO, 120.0, 60, 0.12f),
        InfusionStep(IngredientCategory.PRESTIGE, 104.0, 50, 0.10f),
        InfusionStep(IngredientCategory.SPATIAL, 96.0, 42, 0.085f)
    )

    val WOVEN_ORB_STEPS = listOf(
        InfusionStep(IngredientCategory.THREAD, 68.0, 60, 0.12f),
        InfusionStep(IngredientCategory.ARCANE, 62.0, 50, 0.10f),
        InfusionStep(IngredientCategory.ECHO, 58.0, 42, 0.085f),
        InfusionStep(IngredientCategory.CRYSTAL, 52.0, 34, 0.07f)
    )

    data class WorktableRecipe(
        val nameKey: String,
        val steps: List<InfusionStep>,
        val graded: Boolean = true,
        private val golemCoreCount: Int? = null,
        val assemble: (PocketCasterData.Quality) -> ItemStack
    ) {
        fun preview(): ItemStack = assemble(PocketCasterData.Quality.SOUND)

        val isArtifact: Boolean by lazy { ArtifactItem.`is`(preview()) }

        val golemCores: Int by lazy { golemCoreCount ?: if (isArtifact) ARTIFACT_GOLEM_CORES else 0 }
    }

    val RECIPES = listOf(
        WorktableRecipe(
            "item.hexwright.pocket_caster",
            POCKET_CASTER_STEPS
        ) { quality -> PocketCasterData.create(quality) },
        WorktableRecipe(
            "block.hexwright.warding_box",
            WARDING_BOX_STEPS
        ) { quality -> WardingBoxData.create(quality) },
        WorktableRecipe(
            "item.hexwright.talisman",
            TALISMAN_STEPS
        ) { quality -> TalismanData.create(quality) },
        WorktableRecipe(
            "item.hexwright.hex_engraved_bottle",
            HEX_ENGRAVED_BOTTLE_STEPS
        ) { quality -> com.bluup.hexwright.server.remnant.BottleData.create(quality) },
        WorktableRecipe(
            "item.hexwright.harmonized_pentabox",
            PENTABOX_STEPS
        ) { quality -> PentaboxData.create(quality) },
        WorktableRecipe(
            "item.hexwright.amethyst_core",
            AMETHYST_CORE_STEPS
        ) { quality -> StaffCoreData.create(HexwrightItems.AMETHYST_CORE, quality) },
        WorktableRecipe(
            "item.hexwright.quartz_core",
            QUARTZ_CORE_STEPS
        ) { quality -> StaffCoreData.create(HexwrightItems.QUARTZ_CORE, quality) },
        WorktableRecipe(
            "item.hexwright.scribe_core",
            SCRIBE_CORE_STEPS
        ) { quality -> StaffCoreData.create(HexwrightItems.SCRIBE_CORE, quality) },
        WorktableRecipe(
            "item.hexwright.traveller_core",
            TRAVELLER_CORE_STEPS
        ) { quality -> StaffCoreData.create(HexwrightItems.TRAVELLER_CORE, quality) },
        WorktableRecipe(
            "item.hexwright.echo_core",
            ECHO_CORE_STEPS
        ) { quality -> StaffCoreData.create(HexwrightItems.ECHO_CORE, quality) },
        WorktableRecipe(
            BroomVariant.ETHEREAL.nameKey(),
            BROOM_ETHEREAL_STEPS
        ) { quality -> BroomVariant.ETHEREAL.createStack(quality) },
        WorktableRecipe(
            BroomVariant.STARRY.nameKey(),
            BROOM_STARRY_STEPS
        ) { quality -> BroomVariant.STARRY.createStack(quality) },
        WorktableRecipe(
            BroomVariant.NOCTURNE.nameKey(),
            BROOM_NOCTURNE_STEPS
        ) { quality -> BroomVariant.NOCTURNE.createStack(quality) },
        WorktableRecipe(
            BroomVariant.SWEET_ENCHANTRESS.nameKey(),
            BROOM_SWEET_ENCHANTRESS_STEPS
        ) { quality -> BroomVariant.SWEET_ENCHANTRESS.createStack(quality) },
        WorktableRecipe(
            BroomVariant.SPLINTERED_SWEEPER.nameKey(),
            BROOM_SPLINTERED_SWEEPER_STEPS
        ) { quality -> BroomVariant.SPLINTERED_SWEEPER.createStack(quality) },
        WorktableRecipe(
            BroomVariant.BRISTLEBACK.nameKey(),
            BROOM_BRISTLEBACK_STEPS
        ) { quality -> BroomVariant.BRISTLEBACK.createStack(quality) },
        WorktableRecipe(
            BroomVariant.CROOKED_COMBER.nameKey(),
            BROOM_CROOKED_COMBER_STEPS
        ) { quality -> BroomVariant.CROOKED_COMBER.createStack(quality) },
        WorktableRecipe(
            BroomVariant.WHISPERWIND.nameKey(),
            BROOM_WHISPERWIND_STEPS
        ) { quality -> BroomVariant.WHISPERWIND.createStack(quality) },
        WorktableRecipe(
            BroomVariant.GLEAMGLIDE.nameKey(),
            BROOM_GLEAMGLIDE_STEPS
        ) { quality -> BroomVariant.GLEAMGLIDE.createStack(quality) },
        WorktableRecipe(
            CarpetVariant.PURPLE.nameKey(),
            CARPET_PURPLE_STEPS
        ) { quality -> CarpetVariant.PURPLE.createStack(quality) },
        WorktableRecipe(
            CarpetVariant.RED.nameKey(),
            CARPET_RED_STEPS
        ) { quality -> CarpetVariant.RED.createStack(quality) },
        WorktableRecipe(
            CarpetVariant.TEAL.nameKey(),
            CARPET_TEAL_STEPS
        ) { quality -> CarpetVariant.TEAL.createStack(quality) },
        WorktableRecipe(
            ArmourSet.VEILWALKER.gemTitleKey(),
            VEILWALKER_GEM_STEPS
        ) { quality -> ArmourGemData.create(HexwrightArmour.gem(ArmourSet.VEILWALKER), quality) },
        WorktableRecipe(
            ArmourSet.CANTOR.gemTitleKey(),
            CANTOR_GEM_STEPS
        ) { quality -> ArmourGemData.create(HexwrightArmour.gem(ArmourSet.CANTOR), quality) },
        WorktableRecipe(
            ArmourSet.HEXWARDEN.gemTitleKey(),
            HEXWARDEN_GEM_STEPS
        ) { quality -> ArmourGemData.create(HexwrightArmour.gem(ArmourSet.HEXWARDEN), quality) },
        WorktableRecipe(
            ArmourSet.AUGUR.gemTitleKey(),
            AUGUR_GEM_STEPS
        ) { quality -> ArmourGemData.create(HexwrightArmour.gem(ArmourSet.AUGUR), quality) },
        WorktableRecipe(
            ArmourSet.VENATOR.gemTitleKey(),
            VENATOR_CRYSTAL_STEPS
        ) { quality -> ArmourGemData.create(HexwrightArmour.gem(ArmourSet.VENATOR), quality) },
        WorktableRecipe(
            ArmourSet.DOMITOR.gemTitleKey(),
            DOMITOR_GEM_STEPS
        ) { quality -> ArmourGemData.create(HexwrightArmour.gem(ArmourSet.DOMITOR), quality) },
        WorktableRecipe(
            "item.hexwright.battle_axe",
            BATTLE_AXE_STEPS
        ) { quality -> ItemStack(HexwrightItems.BATTLE_AXES[quality]!!) },
        WorktableRecipe(
            "item.hexwright.battle_hammer",
            BATTLE_HAMMER_STEPS
        ) { quality -> ItemStack(HexwrightItems.BATTLE_HAMMERS[quality]!!) },
        WorktableRecipe(
            "item.hexwright.duelist_short_sword",
            DUELIST_SHORT_SWORD_STEPS
        ) { quality -> ItemStack(HexwrightItems.DUELIST_SHORT_SWORDS[quality]!!) },
        WorktableRecipe(
            "item.hexwright.archer_great_bow",
            ARCHER_GREAT_BOW_STEPS
        ) { quality -> ItemStack(HexwrightItems.ARCHER_GREAT_BOWS[quality]!!) },
        WorktableRecipe(
            "item.hexwright.artisan_signet",
            ARTISAN_SIGNET_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.ARTISAN_SIGNET) },
        WorktableRecipe(
            "item.hexwright.resonant_key",
            RESONANT_KEY_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.RESONANT_KEY) },
        WorktableRecipe(
            "item.hexwright.resonant_ring",
            RESONANT_RING_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.RESONANT_RING) },
        WorktableRecipe(
            "item.hexwright.ring_of_negation",
            RING_OF_NEGATION_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.RING_OF_NEGATION) },
        WorktableRecipe(
            "block.hexwright.field_marker",
            FIELD_MARKER_STEPS,
            graded = false
        ) { ItemStack(HexwrightBlocks.FIELD_MARKER_ITEM) },
        WorktableRecipe(
            "item.hexwright.field_tuner",
            FIELD_TUNER_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.FIELD_TUNER) },
        WorktableRecipe(
            "block.hexwright.resonant_anchor",
            RESONANT_ANCHOR_STEPS,
            graded = false
        ) { ItemStack(HexwrightBlocks.RESONANT_ANCHOR_ITEM) },
        WorktableRecipe(
            "item.hexwright.vault_key",
            VAULT_KEY_STEPS
        ) { quality -> VaultKeyItem.blank(quality) },
        WorktableRecipe(
            "item.hexwright.warders_spectacles",
            WARDERS_SPECTACLES_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.WARDERS_SPECTACLES) },
        WorktableRecipe(
            "item.hexwright.reliquary_seal",
            RELIQUARY_SEAL_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.RELIQUARY_SEAL) },
        WorktableRecipe(
            "item.hexwright.sealed_satchel",
            SEALED_SATCHEL_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.SEALED_SATCHEL) },
        WorktableRecipe(
            "block.hexwright.hexid_tank",
            HEXID_TANK_STEPS,
            graded = false
        ) { ItemStack(HexwrightBlocks.HEXID_TANK_ITEM) },
        WorktableRecipe(
            "block.hexwright.hexid_pipe",
            HEXID_PIPE_STEPS,
            graded = false
        ) { ItemStack(HexwrightBlocks.HEXID_PIPE_ITEM, PIPES_PER_CRAFT) },
        WorktableRecipe(
            "block.hexwright.alembix",
            ALEMBIX_STEPS,
            graded = false
        ) { ItemStack(HexwrightBlocks.ALEMBIX_ITEM) },
        WorktableRecipe(
            "block.hexwright.liquefactrium",
            LIQUEFACTRIUM_STEPS,
            graded = false
        ) { ItemStack(HexwrightBlocks.LIQUEFACTRIUM_ITEM) },
        WorktableRecipe(
            "item.hexwright.eternal_war_hammer",
            ETERNAL_WAR_HAMMER_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.ETERNAL_WAR_HAMMER) },
        WorktableRecipe(
            "item.hexwright.eternal_battle_axe",
            ETERNAL_BATTLE_AXE_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.ETERNAL_BATTLE_AXE) },
        WorktableRecipe(
            "item.hexwright.eternal_great_sword",
            ETERNAL_GREAT_SWORD_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.ETERNAL_GREAT_SWORD) },
        WorktableRecipe(
            "item.hexwright.eternal_odachi",
            ETERNAL_ODACHI_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.ETERNAL_ODACHI) },
        WorktableRecipe(
            "item.hexwright.eternal_scythe",
            ETERNAL_SCYTHE_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.ETERNAL_SCYTHE) },
        WorktableRecipe(
            "item.hexwright.eternal_wakizashi",
            ETERNAL_WAKIZASHI_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.ETERNAL_WAKIZASHI) },
        WorktableRecipe(
            "item.hexwright.frostbite",
            FROSTBITE_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.FROSTBITE) },
        WorktableRecipe(
            "item.hexwright.king_of_purgatory",
            KING_OF_PURGATORY_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.KING_OF_PURGATORY) },
        WorktableRecipe(
            "item.hexwright.silver_starlight",
            SILVER_STARLIGHT_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.SILVER_STARLIGHT) },
        WorktableRecipe(
            "item.hexwright.eternal_bow",
            ETERNAL_BOW_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.ETERNAL_BOW) },
        WorktableRecipe(
            "item.hexwright.starfall_bow",
            STARFALL_BOW_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.STARFALL_BOW) },
        WorktableRecipe(
            BroomVariant.STARBEAM.nameKey(),
            BROOM_STARBEAM_STEPS,
            graded = false
        ) { BroomVariant.STARBEAM.createStack() },
        WorktableRecipe(
            BroomVariant.NIGHTSWEEP.nameKey(),
            BROOM_NIGHTSWEEP_STEPS,
            graded = false
        ) { BroomVariant.NIGHTSWEEP.createStack() },
        WorktableRecipe(
            BroomVariant.TWIN_WHISK.nameKey(),
            BROOM_TWIN_WHISK_STEPS,
            graded = false
        ) { BroomVariant.TWIN_WHISK.createStack() },
        WorktableRecipe(
            "item.hexwright.ring_of_reprisal",
            RING_OF_REPRISAL_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.RING_OF_REPRISAL) },
        WorktableRecipe(
            "item.hexwright.talisman_of_reprieve",
            TALISMAN_OF_REPRIEVE_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.TALISMAN_OF_REPRIEVE) },
        WorktableRecipe(
            "tooltip.hexwright.power_orb.golem",
            GOLEM_POWER_ORB_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.GOLEM_POWER_ORB) },
        WorktableRecipe(
            "tooltip.hexwright.power_orb.cat",
            CAT_POWER_ORB_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.CAT_POWER_ORB) },
        WorktableRecipe(
            "tooltip.hexwright.power_orb.guardian",
            GUARDIAN_POWER_ORB_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.GUARDIAN_POWER_ORB) },
        WorktableRecipe(
            "tooltip.hexwright.power_orb.sanctuary",
            SANCTUARY_POWER_ORB_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.SANCTUARY_POWER_ORB) },
        WorktableRecipe(
            "tooltip.hexwright.power_orb.teeth",
            TEETH_POWER_ORB_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.TEETH_POWER_ORB) },
        WorktableRecipe(
            "tooltip.hexwright.power_orb.dust",
            DUST_POWER_ORB_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.DUST_POWER_ORB) },
        WorktableRecipe(
            "item.hexwright.mantle_of_ascension",
            MANTLE_OF_ASCENSION_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.MANTLE_OF_ASCENSION) },
        WorktableRecipe(
            "item.hexwright.hat_of_ascension",
            HAT_OF_ASCENSION_STEPS,
            graded = false,
            golemCoreCount = 1
        ) { ItemStack(HexwrightItems.HAT_OF_ASCENSION) },
        WorktableRecipe(
            "item.hexwright.cape_of_passage",
            CAPE_OF_PASSAGE_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.CAPE_OF_PASSAGE) },
        WorktableRecipe(
            "item.hexwright.hat_of_passage",
            HAT_OF_PASSAGE_STEPS,
            graded = false,
            golemCoreCount = 1
        ) { ItemStack(HexwrightItems.HAT_OF_PASSAGE) },
        WorktableRecipe(
            "item.hexwright.vault_key.artifact",
            ARTIFACT_VAULT_KEY_STEPS,
            graded = false
        ) { VaultKeyItem.artifact() },
        WorktableRecipe(
            "block.hexwright.hex_loom",
            HEX_LOOM_STEPS,
            graded = false
        ) { ItemStack(HexLoom.ITEM) },
        WorktableRecipe(
            "item.hexwright.woven_orb",
            WOVEN_ORB_STEPS,
            graded = false
        ) { ItemStack(HexLoom.WOVEN_ORB) },
        WorktableRecipe(
            "item.hexwright.necromancer_sword",
            NECROMANCER_SWORD_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.NECROMANCER_SWORD) },
        WorktableRecipe(
            "item.hexwright.spider_bow",
            SPIDER_BOW_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.SPIDER_BOW) },
        WorktableRecipe(
            "item.hexwright.forge_hammer",
            FORGE_HAMMER_STEPS,
            graded = false
        ) { ItemStack(HexwrightItems.FORGE_HAMMER) }
    )

    fun allOutputItems(): Set<Item> {
        val items = LinkedHashSet<Item>()
        for (recipe in RECIPES) {
            for (quality in PocketCasterData.Quality.values()) {
                items.add(recipe.assemble(quality).item)
            }
        }
        return items
    }

    fun defaultRecipe(): Int =
        RECIPES.indices.firstOrNull { !RecipeTablets.requiresDiscovery(RECIPES[it].nameKey) } ?: 0
}
