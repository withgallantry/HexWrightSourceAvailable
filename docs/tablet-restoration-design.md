# The Wright Research Table: design

Status: **built, never played.** All of it is in: the puzzle and its selftest, Inspiration levels
and their sync, the Ruined Tablet and its loot, the Wright Research Table, and the window with its
drag. What has not happened is a single minute of it in a real client - every number below is a
guess at a feel, and the window has only ever been seen as the offline mockup
`tools/tablet_restoration_preview.py` (`python tools/tablet_restoration_preview.py shattered 7`,
rendered to `tools/tablet_restoration_preview.png`).

This is the first thing that spends Inspiration. Collecting it has shipped
(`server/inspiration/`); nothing has ever taken any away.

Decisions marked **[call]** are ones I made rather than ones you gave me; they are the ones to
argue with first.

---

## The loop

1. Slot a **Ruined Tablet** at a **Wright Research Table**. The slab appears on the left with its
   inscription, and some runs of runes knocked out of it. A readout names how much is gone:
   `Shattered · 49% lost`, and a bar counts the breaks.
2. Choose **1, 2 or 3 levels** of Inspiration to stake. Nothing is spent yet; the gauge shows a
   ghost of how much clock that would buy.
3. **Use Inspiration** takes the stake, starts the clock, and deals three paper rubbings into the
   tray. Each shows a run of runes, lit, with the glyphs that sat either side of it in ink.
4. Drag a rubbing onto the break you think it came from. Right: its runes light on the slab and the
   tray refills. Wrong: the gauge shudders and a tenth of the stake is gone - the rubbing stays.
5. Use Inspiration again when nothing in the tray fits: it swaps all three. Fill every break before
   the gauge empties and the tablet is restored - every rune lit, for a moment, before the board
   clears.

## What it plugs into

- **Inspiration gets a sink.** `InspirationState` is already a per-player tally that survives death
  and dimension changes; levels are a reading of that same number, not a second store.
- **Tablets already mean "a recipe you have not met."** `StoneTabletItem` /`ArtifactTabletItem` are
  the couriers for recipe discovery, and `RecipeTablets` already owns which chests seed them. A
  Ruined Tablet is a fourth thing that chest tables can hold, and restoring it is how it becomes a
  Stone Tablet. See [[stone-tablet-recipe-discovery]], [[artifact-recipe-discovery]].
- **Research is the only route since 2026-10-18** (the user's call). Stone Tablets are no longer
  found anywhere: their loot pools are gone from `recipe_tablets.json`, the Decadent Vault and
  Hexwright Base deal Ruined Tablets in their place, a claimed Bindstone hands its claimant a Ruined
  Tablet, and the Quartz Golem's hoard always holds one. The Stone Tablet, Artifact Tablet and
  Artifact Tablet Fragment items were deleted outright later the same day (user's call).
- **The window is an LDLib `.ui` project bound by id**, exactly like the Armament Workbench and the
  Essence Forge: `ArmamentWorkbenchBlockEntity.createUI` is the template to copy, down to
  `UiTemplates.load` + `MenuWidgets.replaceInPlace` + a `fallbackLayout` so a missing project is not
  a crash.

---

## The window

**460 x 328**, which still fits a 640x360 virtual screen at GUI scale 3. The composition follows the
reference you gave rather than my first pass:

- a **title band** across the top (`WRIGHT RESEARCH TABLE`);
- the **slab fills the left**, 230 x 286, with an 11 x 11 grid of runes inside its rim;
- a **gauge on end** between the slab and the right column - the staked Inspiration draining top to
  bottom, with the orb's own specks in it, and the player's current total above it;
- the **right column**, 172 wide: three rubbing cards, then the tablet's slot with the condition
  readout and a breaks-restored bar, then the stake buttons and Use Inspiration, then the player
  inventory.

Two things the reference does that are worth more than the layout:

- **A restored rune lights up and stays lit** (kyanite, with a halo), instead of my first pass's
  parchment patch pasted over the hole. The slab becomes a growing constellation, so progress is
  legible from across the window without reading anything. In game the halo is the same glyph drawn
  four times around itself at low alpha - no second texture, no bloom pass.
- **The rubbing cards use the same lighting to say which part is which**: the run the card puts back
  is drawn lit, its ends are drawn in ink and underlined. Only the ends decide, so they must not
  look like the thing being added.

**The full player inventory is in** - the reference's side-by-side layout buys the height my first
pass did not have, so there is no need for the hotbar-only compromise.

The palette stays `assets/ldlib/textures/menu`, so there is almost no new UI art:

| Part | Texture |
| --- | --- |
| Window frame | `ldlib:menu/wooden-border` (the purple/gold filigree frame), 9-sliced at 12 |
| Title band, gauge trough, buttons | `ldlib:menu/list-box`, with `list-box-select` for the chosen state |
| Tablet slot | `ldlib:menu/item_input`; the Inspiration readout uses `secondary-kyanite-input` |
| Inventory | `ldlib:menu/inv_main` |
| Lit runes, gauge, halos | kyanite `0x0C6E9E -> 0x4CD6FF`, the Inspiration orb's own two colours |

New art, all cut from `references/textures/unused-textures-master` by a `tools/gen_research_assets.py`
in the house style:

| Asset | Source |
| --- | --- |
| `gui/research/slab.png` | `gui/tablet_stone.png`, cropped to (3,2)-(147,181) - the sheet has stray arrow and grid icons on it - at 1.6x so an eleven-wide glyph grid fits inside the rim |
| `gui/research/parchment.png` | `gui/booklet_single_page_paper.png`, the opaque 145x178 page; cards are torn out of it at runtime |
| `gui/research/runes.png` | the sixteen `blocks/overlay_rune_*.png`, as one 4x4 atlas of 16px cells |
| `item/ruined_tablet.png` | `items/artifact_clay_tablet_*` or a recut of `stone_tablet.png` |

The runes are authored as dark overlays for stone, so drawn raw on dark stone they are nearly
invisible. They read as carved when drawn twice: the glyph in near-black at +1,+1, then the glyph in
pale stone (`0xC4BAAA`) on top. Pitted glyphs are the same at 35% alpha; restored ones are the
kyanite pair plus a near-white core at 55%.

## The puzzle

An 11 x 11 grid of glyphs from a 16-rune alphabet. A **break** is a run of 2-3 missing glyphs. A
**scrap** is a rubbing: the missing run, with the glyphs that stood either side of it.

Two generation rules make every break decidable by looking, with no guessing:

1. **A break never touches the edge of its row**, so it always has surviving glyphs on *both* sides.
2. **No two scraps in the deck share the same pair of context ends** — not two correct ones, not a
   correct one and a decoy. The glyphs either side of each break are rewritten at generation time
   until its pair is unique, which is what stops one rubbing fitting two holes once the context is
   down to a single glyph a side.

A **decoy** wears the left end of one break and the right end of another. It is always tempting and
always wrong for a reason on the screen: it matches one end, never both. That is the whole skill -
check both ends, under a clock.

**Pitting** is wear on glyphs that no break is read against: it never touches a context glyph, so it
raises the stated damage and makes the slab harder to scan without ever making an answer ambiguous.

| Tier | Breaks | Run | Context | Decoys | Damage shown | Suggested stake |
| --- | --- | --- | --- | --- | --- | --- |
| Weathered | 4 | 2 | 2 each side | 4 | ~15% | 1 level |
| Scarred | 6 | 2-3 | 2 each side | 7 | ~26% | 1-2 |
| Effaced | 8 | 2-3 | 1 each side | 10 | ~38% | 2 |
| Shattered | 10 | 3 | 1 each side | 14 | ~48% | 2-3 |

Verified both ways. The Python mockup's `check()` cleared 3000 seeds a tier; `/hexwright tablet
selftest` then cleared 1000 a tier on a real dedicated server in 70 ms, reporting the damage ranges
in the table above. The checks are: no break without context on both sides, no break at a row edge,
no two breaks in one row, no two scraps sharing a pair of ends, exactly one scrap fitting each
break. Same shape as `/hexwright weave selftest`, for the same reason - the failure it guards
against is invisible in play until it is infuriating.

Decks come out at exactly breaks + decoys (8, 13, 18, 24). The 9 x 9 grid used to fall a decoy or
two short at the harder tiers, because with one glyph of context there were not always enough
distinct end-pairs left to build them all; eleven columns and eleven rows settled that on its own.

A real Shattered board (`/hexwright tablet show shattered 7`) shows what the tier is like to sit in
front of: of its 24 rubbings, five open with the same left glyph and four more with another, so the
right end is doing nearly all the work. Two scraps sharing a left end can also differ in the middle
by a single rune - which is fine, because the middle never decides, but it is why the card has to
draw its run lit and its ends in ink rather than leaving the player to compare whole strips.

**The damage is rolled once and kept on the item** **[call]**, not on every insert. Rolling per
insert would mean taking the tablet out and back in until an easy one came up.

## The clock: Inspiration as levels

`InspirationState` keeps a raw count; a level is just a reading of it.

- **10 Inspiration = 1 level, flat at every level**, as you asked. `InspirationLevels.POINTS_PER_LEVEL`.
  Crossing one adds a line to the action bar you already get on a gain, plus a chime.
- **1 level = 45 seconds** of clock. Committing spends the points immediately.
- **A wrong placement costs 10% of the committed clock** and burns that scrap, so the same mistake
  cannot be made twice and the deck shrinks as you learn it. Shudder: the bar's fill jitters ±2px
  for 6 ticks and flashes to red, with a stone-crack sound.
- **Dealing is free**, but takes a 0.6s shuffle during which the clock runs **[call]** — that is
  enough to stop deal-spam without a second currency to explain.

Eight breaks takes a fluent player 80-120s, three takes 30-45s, which is what makes the commit a
real decision rather than always 3.

**Fidelity** = clock left / clock committed, at the moment the last break is filled. Measuring it as
a *fraction* is what stops "always commit 3" **[call]**: a bigger clock is a bigger denominator.

| Fidelity | Result |
| --- | --- |
| >= 50% | **Pristine** - the recipe; no Inspiration back (a refund only made the sink leak) |
| >= 20% | **Sound** — the restored tablet |
| > 0 | **Patched** — the restored tablet, tooltip says it was a rough job |

**Running out of time**: the Inspiration is spent and the Ruined Tablet **crumbles** (2026-10-19,
the user's call - it used to survive with its puzzle re-rolled). Its dust puffs off the table and
the owner is told on the action bar. A failed attempt can therefore never teach the answers to a
retry, because there is no retry. Breaking the table mid-attempt crumbles it too, rather than
dropping it.

Closing the window ends the attempt the same way a timeout does, and the window says so.

## What a restored tablet gives

**A random Essence Forge recipe, unlocked on the spot** (your call) - drawn from
`RecipeTablets.get().gatedRecipes()`, never from `artifactRecipes()`, and only from the ones this
restorer has not learned. The Ruined Tablet is spent; no Stone Tablet item is produced, so there is
nothing to carry away and nothing to trade.

That is the same shape `ArtifactTabletItem.use` already has - draw an unlearned recipe for *this*
reader, `RecipeUnlocks.unlock`, sound and a gold chat line - minus the item, so the only new code is
where it is called from.

**Site recipes stay out of the draw** **[call]**: they are learned by standing somewhere that
already has the thing built, and putting them in the pool would let a table in a basement hand over
what a journey is supposed to.

**If there is nothing left to learn, the table refuses to start** rather than taking the stake
first - the check is cheap and runs when the tablet is slotted, so the readout can say so before
anyone commits a level.

Seeding the Ruined Tablet itself is one more `RecipeTablets` config section and one more
`TabletLoot` pool; the existing tiering rule (deep dungeons highest, mundane places lowest) carries
over unchanged.

### Artifact Ruined Tablets (2026-10-18)

An **Artifact Ruined Tablet** (`hexwright:artifact_ruined_tablet`, `ArtifactRuinedTabletItem`)
teaches an unlearned recipe from `artifactRecipes()` when restored. **It comes from workshop chests
and nowhere else** (user's call): `artifact_tablet_loot` in `recipe_tablets.json`, 5% per
`hexwright:chests/deep_dungeon` roll (the kyanite chests inherit it by reference) - about two a
clear of a workshop's forty-odd chests. Issued through `ArtifactProvenance` as its loot is rolled,
taken into the account of whoever carries it, and spent when restored.

Fragments are gone entirely - the items, their recipe and the readable Artifact Tablet were all
deleted. `ArtifactProvenance` now tracks Artifact Ruined Tablets and Golem Cores, with a balance
per player account rather than a count per stack, and only ever logs (see its class comment).

**The stake costs double** (`TabletDamage.inspirationPerStep()`, 2 for an artifact tier): the
slider still picks one to three 45-second steps of clock, but each step costs two levels, so a full
artifact stake is 6 levels for 2:15. Dearer rather than longer, so the bigger prize is paid for in
Inspiration instead of being softened by extra time.

It rolls its own four **`TabletDamage` tiers** - Sundered 8, Riven 10, Ravaged 12, Obliterated 14
breaks, each about four past the plain tier beside it, same weights. A tier rather than a flag on
the session, because the tier is already what is saved, synced and fed to `TabletPuzzle.generate`,
so `damage.isArtifact()` alone decides the reward pool, the window's title and the slab tint.

Twelve and fourteen breaks pass the eleven rows, so **a row can now hold two breaks** at a context
of one (two 3-runs with a glyph a side are 10 of 11 columns). Each keeps its own context - they never
share a glyph, or redrawing the second's ends would undo the first's uniqueness. Rows are only
doubled once every row has one, and a row with one break draws from the RNG exactly as before, so
every plain tablet's seed still gives the same board.

While one is on the table the slab is multiplied by `ResearchArt.ARTIFACT_STONE` (`0xFFE4DE`) - a
warm red cast, stone only, never the runes, so a lit rune is the same kyanite on either kind.

## Architecture

Everything that decides anything is server-side. The client draws and asks.

```
server/research/
  RuinedTabletItem          seed + damage tier in NBT, rolled the first time it is slotted
  TabletPuzzle              pure: generate(seed, tier) -> grid, breaks, deck; no MC types but RandomSource
  TabletDamage              the four tiers and what each one means
  ResearchSession           the attempt: filled breaks, spent rubbings, tray, clock. No player, no
                            level, no item - just the rules, so they read on their own
  ResearchTableBlock        facing, shape, the ticker
  ResearchTableBlockEntity  the slot, the session, and everything that touches the world
  ResearchRewards           draws an unlearned gated recipe and hands it over
  ResearchTableWindow       loads research_table.ui (or its built-in layout), binds it, owns the sync
  ResearchWidget            base of the window's LDLib widgets: the bound table, or a sample in the editor
    ResearchSlabWidget      research_slab - the inscription, and where a rubbing is dropped
    ResearchTrayWidget      research_tray - the rubbings on a leather desk pad, and where a drag starts
    ResearchGaugeWidget     research_gauge - the clock, in a trough framed like the readout's bar
    ResearchReadoutWidget   research_readout - the tablet, its condition, its progress
    ResearchStakeCaptionWidget  research_stake_caption - what the stake buys, or the time left once it runs
    ResearchUseWidget       research_use - Use Inspiration: stakes, and only stakes
    ResearchCancelWidget    research_cancel - Cancel Research, over the stake row while the clock runs
    ResearchRedealWidget    research_redeal - Fresh Rubbings, in the tray's top-right corner
  InspirationBarWidget      inspiration_bar - the HUD's Inspiration bar, in the window
  ResearchStakeWidget       the stake slider, built in code in place of the layout's `stake` placeholder
  ResearchArt               the palette, runes and rubbing cards the widgets share
  ResearchCommand           /hexwright tablet selftest | show <tier> <seed>
  TabletSelfTest            the puzzle invariants, the window build, and a played attempt
```

**The puzzle never crosses the wire.** `TabletPuzzle.generate` is deterministic and identical on
both sides, so what is sent is the seed and the tier; the client rebuilds the same grid, the same
breaks and the same deck. Nothing is hidden from the client anyway - the puzzle is meant to be
solved by looking at it - so what it holds is a copy, not an advantage, and the server re-checks
every placement against its own. Same shape as the Hexweave model shared by `ThreadDraft` on both
sides.

**No packets of our own.** The first draft of this design listed four. LDLib already has the
channel: a widget's `detectAndSendChanges` / `writeUpdateInfo` carries the session's couple of
dozen bytes, and `writeClientAction` carries everything the player does. The only packet this feature adds is
`INSPIRATION_SYNC_S2C`, which supersedes the old "no sync packet" decision - the window cannot
offer you 1/2/3 levels without knowing what you have. The action-bar line stays as it is.

| Carried by | What |
| --- | --- |
| `ResearchTableWindow.Sync`: `writeInitialData` / `writeUpdateInfo(1)` | the whole session: seed, tier, filled mask, spent mask, tray, stake, clock, last outcome |
| `research_slab`: `writeClientAction(1)` | a placement: tray slot, break index |
| `ResearchStakeWidget`: `writeClientAction(2)` | choosing the stake: 1-3 (not 1 - a group routes its children's actions under that id) |
| `research_use`: `writeClientAction(1)` | Use Inspiration - the stake; ignored mid-attempt |
| `research_redeal`: `writeClientAction(1)` | Fresh Rubbings - fill the tray, or swap a full one |
| `research_cancel`: `writeClientAction(1)` | Cancel Research - the owner abandons the attempt |
| `INSPIRATION_SYNC_S2C` | the player's total, on join and on change |

The running clock is resent every 5 ticks and everything else the moment it changes, which is
cheaper than it sounds (about 40 bytes a second) and spares the client any prediction logic.

### Three decisions the build made

**The window is an LDLib project** (since 2026-10-18, at the user's request; it began as one
hand-drawn widget built in code). `research_table.ui` lays out one widget per piece, so the window
can be rearranged in LDLib's editor, where every piece draws a sample attempt. The project was
exported once from `ResearchTableWindow.defaultLayout()`, which stays as the fallback with the same
widgets and ids. The `research_*` widgets are bound by type; what is bound by id is what has to be
swapped for a real widget - `tablet_slot`, `player_inv_0..35`, and `stake`, a plain group whose size
and background (the potion-glass frame) the code-built slider takes over, because LDLib's
`SliderWidget` cannot live in a `.ui` a server reads.

**The drag is LDLib's own.** The tray hands the screen a `Rubbing` and a card texture with
`ModularUIGuiContainer.setDraggingElement`; the slab asks the screen what is being carried, lights
the break under the cursor, and sends the placement on release. Two departures from LDLib's stock
dragging: the card is lifted on the press, not when the cursor leaves the card (a card that stays put
until then looks stuck), and the texture draws the card where it was taken hold of rather than in
the 40px square LDLib hands every dragged texture. A pick-up is refused while the cursor holds an
item, so a drop over an inventory slot can never put that item down, and the stake's slider ignores
a drag while a rubbing is carried across it.

**Fresh rubbings are their own button** (2026-10-18, the user's call). Use Inspiration used to deal
mid-attempt as well, and pressing the button that had just taken the stake felt like paying it
again. It now only stakes and goes dark once the clock runs; a small button with a circling-arrows
icon in the tray's top-right corner fills or swaps the tray, and says in its tooltip that it costs
no Inspiration.

**The stake row turns into Cancel Research while the clock runs** (2026-10-18, the user's call). The
slider, Use Inspiration and the "N levels" caption are gone mid-attempt - none of them can do
anything then - and a full-width Cancel Research button takes their place, with the caption under
it reading the time left (the readout beside the tablet no longer shows the clock). Cancelling is
running out of time on purpose: the stake is lost and the tablet crumbles (its tooltip warns), so it
is never a cheaper way out than failing, and only whoever staked the attempt can do it.
The rubbings lie on a dark leather desk pad (`gui/research/leather.png`, generated) inset from its
tooled edge, and a carried one is drawn at 65% so the slab's ends show through it while lining up.

**The sync belongs to no piece of the layout.** `ResearchTableWindow.Sync`, a zero-sized widget added
after binding, carries the session, so deleting or duplicating a piece in the editor cannot stop the
window updating.

**The session lives on the table, not on the player.** A clock has to run whether or not a window
happens to be open, and it makes two people at one table a feature rather than a bug: either can
place a rubbing, and the recipe goes to whoever staked the Inspiration.

### And one rule the rules needed

**A wrong rubbing is never burned.** Tidier to look at, and a trap: the one burned could be the
only one that fits some other break, and the player would be left with a tablet that cannot be
finished and no way to see why. A wrong answer costs clock and nothing else.

Three things the codebase has already been bitten by, that this respects:

- Every render method on a widget LDLib might reflect needs `@Environment(EnvType.CLIENT)`, or
  deserializing a project kills a dedicated server outright. See [[ldlib-custom-widget-env-strip]].
  The research widgets are reflected now; `TabletSelfTest`'s window check builds the window from
  `research_table.ui` on a dedicated server, and fails if the layout lacks a piece the game needs.
- No `slider` or `phantom_fluid` in any `.ui` - LDLib's own two unannotated widgets.
- Debug lines go through a new `HexwrightDebug.TABLET` area, not a widened existing one.

### The rune atlas is a white mask

`gui/research/runes.png` is the sixteen `overlay_rune` glyphs with their colour thrown away and
only their alpha kept. The source art is dark, meant to be laid over stone, and `setColor`
multiplies: a dark texture cannot be tinted up to a lit kyanite rune, but a white one can be tinted
to anything. That is what lets one atlas serve carved, pitted, inked and restored.

## Review after the first client run (2026-10-17)

The first screenshot from a real client, and a second pass over the engineering, found these.

**What the window got wrong**

- **Nobody could tell how to start.** The 1/2/3 buttons *started* the attempt, and Use Inspiration
  did nothing until one had - the reverse of the design, where you choose a stake and then press
  Use Inspiration. Now 1/2/3 only choose, and Use Inspiration begins.
- **Every empty state was a blank.** The tray was a void and the gauge an empty trough. The tray now
  says what to do in three numbered lines, the gauge shows a ghost of the chosen stake (red if you
  cannot afford it), and your own Inspiration is printed under the button.
- **A grey grid over the purple inventory.** `SlotWidget` defaults to LDLib's `gui/slot.png`; the
  backgrounds are now cleared so `inv_main` and `item_input` show through.
- The stake buttons had no label, `0/4` ran past its bar into the frame, and the title was jammed
  against the frame's edge.

**What the engineering got wrong**

- **You could walk off with the tablet mid-attempt** - by hand, by shift-click or by hopper - and
  keep a tablet whose answers you had just been shown, then put it back for a near-free second
  recipe. The slot's `mayPickup`, `SlotWidget.canTakeStack` and `Container.canTakeItem` now all
  refuse while the clock runs.
- **Breaking the table mid-attempt did the same**, dropping the tablet with its seed intact. It is
  re-drawn before it drops.
- **The slot logic lived in `setChanged()`**, which vanilla calls from all over and which also ran
  client-side - making the slot sync a second writer to the client's session, racing the widget's
  own update. It is now `slotChanged()`, server-only, called from the four places the slot actually
  changes (one of which, `removeItemNoUpdate`, the old path missed entirely).
- The board lived in `client/` while doing as much server work as drawing; it is in
  `server/research` now, with every drawing method still `@Environment(CLIENT)`.
- The controls are drawn and hit-tested by the board instead of being `ButtonWidget`s: one input
  path, and no opaque button texture stacked over a highlight that has to show through it.
- Smaller: enums cross the wire by `writeEnum` rather than hand-rolled ordinal bytes; swapping a
  full tray no longer hands straight back the same three when there are others; and the finished
  tablet stays on the slab, every rune lit, for two and a half seconds instead of vanishing on the
  last placement.

**And the selftest now plays it.** A fake player is refused at half a level, stakes two, cannot
move the tablet or the stake mid-attempt, drops a wrong rubbing (180 ticks, the rubbing stays),
solves the tablet, is paid a recipe and no rebate, and finally breaks a table mid-attempt
to check the tablet comes out re-drawn.

## Second client pass: the frame, the breaks and the gauge (2026-10-17)

- **The frame was sliced at the wrong size.** `wooden-border`'s rail is nine texels with a corner
  ornament reaching ten, and a `ResourceBorderTexture`'s corner size is both how big it is drawn and
  how much of the image it takes. Sliced at four, as the smaller chest windows do, the inner five
  texels of the rail landed in the stretched middle and came out two and a half times as wide as
  they were tall - the thick, lopsided border the content then sat on. Sliced at ten it is the art's
  own nine pixels on every side, and everything sits five inside it. The title is plain text inside
  the frame now, not a second box.
- **Nothing is rescaled at runtime any more.** The slab was drawn at 1.6x with nearest filtering, so
  its texels came out one or two pixels wide; it is baked smooth at 230x286 and drawn 1:1. The cards
  squashed the whole 178px page into 30px; they are cut from a strip torn at the card's height, drawn
  in two pieces so a short card keeps its ragged right end.
- **The breaks are hollows, not black boxes.** The reference pack has no broken stone, so
  `gen_research_table_assets.py` cuts spalls out of the slab's own stone: lumpy, chipped outlines
  over each missing rune, read through rim light rather than darkness - shadow along the top and left
  inner walls, light on the bottom and right, a pale broken lip just outside. Three variants each for
  two- and three-rune breaks. A restored break keeps its hollow, so the runes are lit where the stone
  came away. A target glows in its own broken outline instead of a cyan rectangle.
- **The gauge is fluid.** `fluid_blue_goo`, turned 14 degrees from teal (182) to the Inspiration orbs'
  kyanite (196), tiled up from the bottom and animated off the wall clock at its 30 frames, with a
  pale surface line. The stake preview before an attempt is the same fluid at half strength.
- **`tools/tablet_restoration_preview.py` now renders the real window** - the baked textures,
  LDLib's panels and vanilla's font out of Loom's cached client jar - in place of the design-stage
  mockup, which no longer matched anything. Since 2026-10-18 it reads the layout out of
  `research_table.ui` (the editor's working copy first), so it shows whatever was last saved.

## Build order

| Step | Effort | What |
| --- | --- | --- |
| 1 | S | **done** - `TabletPuzzle` + `TabletSelfTest` + `/hexwright tablet selftest\|show`. Pure Java, no client, proved headlessly. |
| 2 | S | **done** - `InspirationLevels`, `INSPIRATION_SYNC_S2C` + `ClientInspiration`, the level on the action bar and a line when one is crossed. |
| 3 | M | **done** - `RuinedTabletItem`, its art, seven loot tiers in `recipe_tablets.json`, and `ResearchRewards`. |
| 4 | M | **done** - `ResearchTableBlock` + block entity + slot, block model and recipe. |
| 5 | L | **done** - the window: the slab, the gauge, the tray, the drag and the sync. Split into LDLib widgets and `research_table.ui` on 2026-10-18. |
| 6 | S | Tuning against a real playtest - every number above is a guess at a feel. |

Overall: **medium-large**. Nothing here needs a new system; the risk is all in step 5, where "drag a
thing onto another thing" has to work inside LDLib's widget tree, and in whether 45 seconds a level
is anywhere near right.

## Open questions

1. **The alphabet.** Sixteen `overlay_rune` glyphs is what the reference pack has. Hex Casting's own
   pattern strokes would be more on-theme but much harder to tell apart at 16px.
2. **Multiplayer.** One session per player, so two people at one table would fight over the single
   slot. A second slot, or a "busy" state, if that is a real case on your server.
