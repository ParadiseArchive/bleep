package io.hookdev.bleep.gametest;

import io.hookdev.bleep.Bleep;
import io.hookdev.bleep.BleepContent;
import io.hookdev.bleep.block.HomebaseBlock;
import io.hookdev.bleep.entity.Accessory;
import io.hookdev.bleep.entity.Activity;
import io.hookdev.bleep.entity.BleepEntity;
import io.hookdev.bleep.entity.Bond;
import io.hookdev.bleep.entity.Display;
import io.hookdev.bleep.entity.Emotion;
import io.hookdev.bleep.entity.NaughtyGoal;
import io.hookdev.bleep.entity.Obsession;
import io.hookdev.bleep.item.BleepCoreItem;
import io.hookdev.bleep.network.BleepCommand;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-world tests, run with {@code ./gradlew runGameTestServer} (or {@code /test runall} in a dev client).
 * They all use the empty "bench" structure (7 wide, 3 deep, standing on the ground at y 0) and set up their own bleep.
 */
public final class BleepGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Bleep.MODID);
    /** How long each test may take, in ticks. */
    private static final Map<String, Integer> MAX_TICKS = new LinkedHashMap<>();

    static {
        test("a_bleep_never_dies", BleepGameTests::aBleepNeverDies, 20);
        test("damage_drains_the_battery", BleepGameTests::damageDrainsTheBattery, 20);
        test("an_empty_bleep_sleeps_and_charges", BleepGameTests::anEmptyBleepSleepsAndCharges, 40);
        test("a_low_battery_makes_it_tired", BleepGameTests::aLowBatteryMakesItTired, 20);
        test("petting_makes_it_happy", BleepGameTests::pettingMakesItHappy, 20);
        test("only_the_owner_gives_orders", BleepGameTests::onlyTheOwnerGivesOrders, 20);
        test("placing_a_homebase_moves_your_bleep_in", BleepGameTests::placingAHomebaseMovesYourBleepIn, 20);
        test("go_home_drives_it_home", BleepGameTests::goHomeDrivesItHome, 200);
        test("an_empty_bleep_goes_home_to_charge", BleepGameTests::anEmptyBleepGoesHomeToCharge, 100);
        test("the_homebase_lights_up_while_it_charges", BleepGameTests::theHomebaseLightsUpWhileItCharges, 60);
        test("a_broken_homebase_leaves_it_homeless", BleepGameTests::aBrokenHomebaseLeavesItHomeless, 60);
        test("petting_raises_the_bond_five_times_a_day", BleepGameTests::pettingRaisesTheBondFiveTimesADay, 20);
        test("treats_and_the_favourite_raise_the_bond", BleepGameTests::treatsAndTheFavouriteRaiseTheBond, 20);
        test("hitting_it_or_switching_it_off_lowers_the_bond", BleepGameTests::hittingItOrSwitchingItOffLowersTheBond, 20);
        test("a_new_level_is_celebrated", BleepGameTests::aNewLevelIsCelebrated, 20);
        test("it_dawdles_then_does_it_anyway", BleepGameTests::itDawdlesThenDoesItAnyway, 100);
        test("in_danger_it_always_listens", BleepGameTests::inDangerItAlwaysListens, 20);
        test("a_new_mob_gets_a_thought", BleepGameTests::aNewMobGetsAThought, 120);
        test("it_brings_its_favourite_to_its_owner", BleepGameTests::itBringsItsFavouriteToItsOwner, 300);
        test("it_fetches_a_thrown_music_disc", BleepGameTests::itFetchesAThrownMusicDisc, 400);
        test("a_name_always_shows", BleepGameTests::aNameAlwaysShows, 20);
        test("a_torch_needs_a_friend", BleepGameTests::aTorchNeedsAFriend, 20);
        test("its_torch_lights_the_way", BleepGameTests::itsTorchLightsTheWay, 40);
        test("it_fights_for_its_owner", BleepGameTests::itFightsForItsOwner, 100);
        test("a_buddy_brings_what_it_finds", BleepGameTests::aBuddyBringsWhatItFinds, 300);
        test("a_bored_bleep_steals_a_cookie", BleepGameTests::aBoredBleepStealsACookie, 300);
        test("a_bored_bleep_moves_a_torch", BleepGameTests::aBoredBleepMovesATorch, 400);
        test("redstone_charges_it", BleepGameTests::redstoneChargesIt, 20);
        test("redstone_wakes_an_empty_bleep", BleepGameTests::redstoneWakesAnEmptyBleep, 40);
        test("a_power_bank_tops_it_up", BleepGameTests::aPowerBankTopsItUp, 20);
        test("lightning_overcharges_it", BleepGameTests::lightningOverchargesIt, 40);
        test("dye_paints_it", BleepGameTests::dyePaintsIt, 20);
        test("it_wears_a_hat_and_a_sticker", BleepGameTests::itWearsAHatAndASticker, 20);
        test("its_name_scrolls_across", BleepGameTests::itsNameScrollsAcross, 20);
        test("an_arrow_points_home", BleepGameTests::anArrowPointsHome, 40);
        test("its_diary_starts_the_day_it_was_built", BleepGameTests::itsDiaryStartsTheDayItWasBuilt, 20);
        test("a_new_mob_goes_in_the_bleepedia", BleepGameTests::aNewMobGoesInTheBleepedia, 120);
        test("a_special_block_is_a_find", BleepGameTests::aSpecialBlockIsAFind, 100);
        test("opening_the_bleepedia_reads_the_news", BleepGameTests::openingTheBleepediaReadsTheNews, 20);
        test("it_dances_to_music", BleepGameTests::itDancesToMusic, 60);
        test("it_sleeps_by_its_owners_bed", BleepGameTests::itSleepsByItsOwnersBed, 100);
        test("it_digs_something_up", BleepGameTests::itDigsSomethingUp, 100);
        test("an_iron_golem_is_its_big_brother", BleepGameTests::anIronGolemIsItsBigBrother, 100);
        test("an_abandoned_bleep_wakes_with_redstone", BleepGameTests::anAbandonedBleepWakesWithRedstone, 20);
        test("it_is_called_bleep_until_you_rename_it", BleepGameTests::itIsCalledBleepUntilYouRenameIt, 20);
        test("a_name_tag_does_not_stick", BleepGameTests::aNameTagDoesNotStick, 20);
        test("its_magnet_fills_its_storage", BleepGameTests::itsMagnetFillsItsStorage, 100);
        test("it_fights_with_a_sword_from_its_storage", BleepGameTests::itFightsWithASwordFromItsStorage, 100);
        test("it_shows_off_and_keeps_it", BleepGameTests::itShowsOffAndKeepsIt, 120);
        test("it_brings_the_ball_back", BleepGameTests::itBringsTheBallBack, 300);
        test("two_bleeps_play_together", BleepGameTests::twoBleepsPlayTogether, 40);
        test("petting_one_makes_the_other_jealous", BleepGameTests::pettingOneMakesTheOtherJealous, 20);
        test("a_happy_bleep_cheers_up_a_sad_one", BleepGameTests::aHappyBleepCheersUpASadOne, 220);
        test("llama_spit_makes_it_glitch", BleepGameTests::llamaSpitMakesItGlitch, 20);
        test("a_bed_by_its_home_is_a_comfort", BleepGameTests::aBedByItsHomeIsAComfort, 20);
        test("ten_bleeps_is_plenty", BleepGameTests::tenBleepsIsPlenty, 20);
        test("two_bleeps_say_hi", BleepGameTests::twoBleepsSayHi, 200);
        test("two_bleeps_have_a_chat", BleepGameTests::twoBleepsHaveAChat, 260);
        test("tag_has_one_who_is_it", BleepGameTests::tagHasOneWhoIsIt, 20);
        test("bleeps_fight_with_thunder_clouds", BleepGameTests::bleepsFightWithThunderClouds, 140);
        test("petting_stops_a_fight", BleepGameTests::pettingStopsAFight, 20);
        test("two_fights_make_rivals", BleepGameTests::twoFightsMakeRivals, 20);
        test("staying_bleeps_still_chat", BleepGameTests::stayingBleepsStillChat, 60);
        test("it_welcomes_its_owner_back", BleepGameTests::itWelcomesItsOwnerBack, 60);
        test("a_birthday_with_cake", BleepGameTests::aBirthdayWithCake, 160);
        test("it_does_tricks_it_knows", BleepGameTests::itDoesTricksItKnows, 60);
        test("it_hides", BleepGameTests::itHides, 20);
        test("it_counts_then_seeks", BleepGameTests::itCountsThenSeeks, 300);
        test("a_farmer_harvests_and_replants", BleepGameTests::aFarmerHarvestsAndReplants, 200);
        test("tidy_up_puts_its_storage_in_a_chest", BleepGameTests::tidyUpPutsItsStorageInAChest, 200);
        test("the_stay_radius_can_change", BleepGameTests::theStayRadiusCanChange, 20);
        test("chips_are_installed_for_good", BleepGameTests::chipsAreInstalledForGood, 20);
        test("it_writes_a_letter", BleepGameTests::itWritesALetter, 300);
        test("it_saw_a_star_fall", BleepGameTests::itSawAStarFall, 20);
        test("a_fallen_star_holds_shards", BleepGameTests::aFallenStarHoldsShards, 20);
        test("a_homebase_drops_itself", BleepGameTests::aHomebaseDropsItself, 20);
        test("it_guards_your_things", BleepGameTests::itGuardsYourThings, 200);
        test("it_brings_food_when_you_are_hurt", BleepGameTests::itBringsFoodWhenYouAreHurt, 300);
        test("it_warns_for_a_creeper", BleepGameTests::itWarnsForACreeper, 40);
        test("a_fisher_catches_something", BleepGameTests::aFisherCatchesSomething, 500);
        test("a_smelter_fills_the_furnace", BleepGameTests::aSmelterFillsTheFurnace, 400);
        test("a_gardener_plants_flowers", BleepGameTests::aGardenerPlantsFlowers, 400);
        test("what_it_does_most_becomes_its_hobby", BleepGameTests::whatItDoesMostBecomesItsHobby, 20);
        test("a_kilometre_is_a_milestone", BleepGameTests::aKilometreIsAMilestone, 140);
        test("it_shows_the_way", BleepGameTests::itShowsTheWay, 300);
        test("it_dreams", BleepGameTests::itDreams, 20);
        test("it_buries_a_treasure", BleepGameTests::itBuriesATreasure, 400);
        test("it_finds_what_you_hid", BleepGameTests::itFindsWhatYouHid, 1200);
        test("it_goes_shopping", BleepGameTests::itGoesShopping, 400);
        test("it_brings_the_emeralds_back", BleepGameTests::itBringsTheEmeraldsBack, 400);
        test("it_rides_along", BleepGameTests::itRidesAlong, 200);
        test("it_reacts_to_what_you_do", BleepGameTests::itReactsToWhatYouDo, 140);
        test("the_core_needs_its_parts", BleepGameTests::theCoreNeedsItsParts, 20);
        test("the_core_puts_a_bleep_together", BleepGameTests::theCorePutsABleepTogether, 200);
        test("the_core_turns_up_in_chests", BleepGameTests::partsTurnUpInChests, 20);
    }

    private static final BlockPos SPOT = new BlockPos(3, 0, 1), HOME = new BlockPos(0, 0, 1), FAR = new BlockPos(6, 0, 1);

    private BleepGameTests() {}

    private static void test(String name, Consumer<GameTestHelper> function, int maxTicks) {
        FUNCTIONS.register(name, () -> function);
        MAX_TICKS.put(name, maxTicks);
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(BleepGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Bleep.id("default"));
        for (var function : FUNCTIONS.getEntries()) {
            event.registerTest(function.getId(), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, function.getId()),
                    new TestData<>(environment, Bleep.id("bench"), MAX_TICKS.get(function.getId().getPath()), 0, true)));
        }
    }

    /** A bleep that belongs to a test player. */
    private static BleepEntity bleep(GameTestHelper helper, Player owner) {
        BleepEntity bleep = helper.spawnWithNoFreeWill(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        return bleep;
    }

    private static void aBleepNeverDies(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1000);
        helper.assertTrue(bleep.isAlive(), "a bleep should never die");
        helper.assertTrue(bleep.getHealth() == bleep.getMaxHealth(), "a bleep has no health to lose");
        helper.assertTrue(bleep.asleep(), "a huge hit should empty the battery, and the bleep switches off");
        helper.succeed();
    }

    private static void damageDrainsTheBattery(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 3);
        float expected = BleepEntity.FULL - 3 * BleepEntity.DAMAGE_COST;
        helper.assertTrue(Math.abs(bleep.battery() - expected) < 0.01f, "a hit of 3 should cost 6%, battery is " + bleep.battery());
        helper.succeedWhen(() -> helper.assertTrue(bleep.emotion() == Emotion.SURPRISED, "a hit should surprise it"));
    }

    private static void anEmptyBleepSleepsAndCharges(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.setBattery(0);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(bleep.emotion() == Emotion.ASLEEP, "an empty bleep should be asleep, it shows " + bleep.emotion());
            helper.assertTrue(bleep.battery() > 0.3f, "it should charge itself while it sleeps, battery is " + bleep.battery());
            helper.succeed();
        });
    }

    private static void aLowBatteryMakesItTired(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.setBattery(10);
        helper.succeedWhen(() -> helper.assertTrue(bleep.emotion() == Emotion.TIRED, "below 20% it should look tired, it shows " + bleep.emotion()));
    }

    private static void pettingMakesItHappy(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        owner.setShiftKeyDown(true);
        bleep.mobInteract(owner, InteractionHand.MAIN_HAND);
        helper.assertFalse(bleep.isOrderedToSit(), "petting should not tell it to stay");
        helper.succeedWhen(() -> helper.assertTrue(bleep.emotion() == Emotion.EXCITED, "petting should make it excited, it shows " + bleep.emotion()));
    }

    /** A test player standing in the middle of the bench. */
    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(3.5, 0, 1.5));
        player.snapTo(at.x, at.y, at.z);
        return player;
    }

    /** A homebase in the bench, facing east, without anyone living in it yet. */
    private static BlockPos homebase(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, BleepContent.HOMEBASE.get().defaultBlockState().setValue(HomebaseBlock.FACING, Direction.EAST));
        return helper.absolutePos(pos);
    }

    private static void onlyTheOwnerGivesOrders(GameTestHelper helper) {
        Player owner = player(helper), stranger = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        helper.assertFalse(BleepCommand.obey(stranger, bleep, BleepCommand.STAY, 1), "a bleep only listens to its owner");
        helper.assertFalse(bleep.isOrderedToSit(), "a stranger should not make it stay");
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.STAY, 1) && bleep.isOrderedToSit(), "Stay should make it stay");
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.FOLLOW, 1) && !bleep.isOrderedToSit(), "Follow should make it follow");
        helper.assertFalse(BleepCommand.obey(owner, bleep, BleepCommand.GO_HOME, 1), "a bleep without a homebase cannot go home");
        helper.succeed();
    }

    private static void placingAHomebaseMovesYourBleepIn(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        BlockPos home = homebase(helper, HOME);
        BleepContent.HOMEBASE.get().setPlacedBy(helper.getLevel(), home, helper.getLevel().getBlockState(home), owner, ItemStack.EMPTY);
        helper.assertTrue(GlobalPos.of(helper.getLevel().dimension(), home).equals(bleep.home()), "the bleep should move into the new homebase");
        helper.assertTrue(HomebaseBlock.resident(helper.getLevel(), home) == bleep, "the homebase should know who lives there");
        helper.succeed();
    }

    private static void goHomeDrivesItHome(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), FAR);
        bleep.tame(owner);
        BlockPos home = homebase(helper, HOME);
        bleep.setHome(GlobalPos.of(helper.getLevel().dimension(), home));
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.GO_HOME, 1), "with a homebase, Go Home should work");
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.onHome(), "it should drive onto its homebase");
            helper.assertTrue(bleep.isOrderedToSit(), "and stay there");
        });
    }

    private static void anEmptyBleepGoesHomeToCharge(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        BlockPos home = homebase(helper, HOME);
        bleep.setHome(GlobalPos.of(helper.getLevel().dimension(), home));
        bleep.setBattery(0);
        helper.runAfterDelay(BleepEntity.GOES_HOME_AFTER + 10, () -> {
            helper.assertTrue(bleep.onHome(), "an empty bleep should go home");
            helper.assertTrue(bleep.asleep(), "and charge there asleep, until it is full");
            helper.assertTrue(bleep.activity() == Activity.CHARGING, "its screen should say it is charging, it says " + bleep.activity());
            helper.assertTrue(bleep.battery() > 0.5f, "it should charge at home, battery is " + bleep.battery());
            helper.succeed();
        });
    }

    private static void theHomebaseLightsUpWhileItCharges(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        BlockPos home = homebase(helper, HOME);
        bleep.setHome(GlobalPos.of(helper.getLevel().dimension(), home));
        bleep.setBattery(50);
        bleep.dock();
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(bleep.battery() > 50.3f, "a docked bleep should charge, battery is " + bleep.battery());
            helper.assertTrue(helper.getLevel().getBlockState(home).getValue(HomebaseBlock.CHARGING), "the light should be on while it charges");
            bleep.setBattery(BleepEntity.FULL);
            helper.runAfterDelay(5, () -> {
                helper.assertFalse(helper.getLevel().getBlockState(home).getValue(HomebaseBlock.CHARGING), "the light should go out when it is full");
                helper.assertTrue(bleep.activity() == Activity.AT_HOME, "full at home it should be resting, it says " + bleep.activity());
                helper.succeed();
            });
        });
    }

    private static void aBrokenHomebaseLeavesItHomeless(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        BlockPos home = homebase(helper, HOME);
        bleep.setHome(GlobalPos.of(helper.getLevel().dimension(), home));
        helper.setBlock(HOME, Blocks.AIR);
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.home() == null, "without its homebase it should be homeless");
            helper.assertTrue(bleep.emotion() == Emotion.SAD, "and a little sad about it, it shows " + bleep.emotion());
        });
    }

    /** Its favourite item made the compass, so the tests know what it is. */
    private static final int COMPASS = Obsession.ALL.indexOf(Obsession.ALL.stream().filter(o -> o.id().equals("compass")).findFirst().orElseThrow());

    private static void pettingRaisesTheBondFiveTimesADay(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        for (int i = 0; i < 7; i++) bleep.pet();
        helper.assertTrue(bleep.bond() == Bond.PETS_PER_DAY * Bond.PET, "petting counts five times a day, bond is " + bleep.bond());
        helper.succeed();
    }

    private static void treatsAndTheFavouriteRaiseTheBond(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.setObsession(COMPASS);
        helper.assertTrue(bleep.give(owner, new ItemStack(Items.COOKIE)), "a cookie is a treat");
        helper.assertTrue(bleep.bond() == Bond.GIFT, "a treat is worth 3, bond is " + bleep.bond());
        helper.assertFalse(bleep.give(owner, new ItemStack(Items.DIRT)), "dirt is not a treat");
        helper.assertFalse(bleep.revealed(), "its favourite is a secret until you find it");
        helper.assertTrue(bleep.give(owner, new ItemStack(Items.COMPASS)), "it takes its favourite item");
        helper.assertTrue(bleep.revealed(), "giving it its favourite shows you what it is");
        helper.assertTrue(bleep.bond() == Bond.GIFT + Bond.FAVOURITE, "its favourite is worth 10, bond is " + bleep.bond());
        helper.assertFalse(bleep.give(owner, new ItemStack(Items.COMPASS)), "only one favourite a day counts");
        helper.assertTrue(bleep.bond() == Bond.GIFT + Bond.FAVOURITE, "a second favourite the same day is not worth more");
        helper.succeed();
    }

    private static void hittingItOrSwitchingItOffLowersTheBond(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.setBond(50);
        bleep.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(owner), 1);
        helper.assertTrue(bleep.bond() == 50 + Bond.HIT_BY_OWNER, "a hit from its owner costs 5, bond is " + bleep.bond());
        bleep.setBattery(0);
        helper.assertTrue(bleep.bond() == 50 + Bond.HIT_BY_OWNER + Bond.SWITCHED_OFF, "switching off costs 3, bond is " + bleep.bond());
        helper.succeed();
    }

    private static void aNewLevelIsCelebrated(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.setBond(24);
        helper.assertTrue(Bond.level(bleep.bond()) == 0, "24 is still New");
        bleep.pet();
        helper.assertTrue(Bond.level(bleep.bond()) == 1, "25 is Friend");
        helper.succeedWhen(() -> helper.assertTrue(bleep.emotion() == Emotion.EXCITED, "a new level makes it excited"));
    }

    private static void itDawdlesThenDoesItAnyway(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.STAY, 0), "it will do it");
        helper.assertFalse(bleep.isOrderedToSit(), "a new bleep that rolls low dawdles first");
        helper.assertTrue(bleep.dawdling(), "it should be dawdling");
        helper.runAfterDelay(5, () -> helper.assertTrue(bleep.emotion() == Emotion.STUBBORN, "it pulls a stubborn face, it shows " + bleep.emotion()));
        helper.succeedWhen(() -> helper.assertTrue(bleep.isOrderedToSit() && !bleep.dawdling(), "after dawdling it stays anyway"));
    }

    private static void inDangerItAlwaysListens(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.setBattery(10);
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.STAY, 0), "it will do it");
        helper.assertTrue(bleep.isOrderedToSit(), "with a low battery it does not dawdle");
        helper.succeed();
    }

    private static void aNewMobGetsAThought(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        // looked up by name: 26.3 and 26.1 keep their entity types in different classes
        @SuppressWarnings("unchecked")
        EntityType<net.minecraft.world.entity.Mob> cow = (EntityType<net.minecraft.world.entity.Mob>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("cow"));
        helper.spawnWithNoFreeWill(cow, FAR);
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.seenMobs().contains("minecraft:cow"), "it should have a look at the cow");
            helper.assertTrue(bleep.thought().equals("mob.bleep.cow"), "and think about it, it thinks " + bleep.thought());
        });
    }

    private static void itBringsItsFavouriteToItsOwner(GameTestHelper helper) {
        Player owner = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(0.5, 0, 1.5));
        owner.snapTo(at.x, at.y, at.z);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        bleep.setObsession(COMPASS);
        helper.spawnItem(Items.COMPASS, new Vec3(5.5, 0.2, 1.5));
        helper.succeedWhen(() -> {
            helper.assertTrue(owner.getInventory().contains(new ItemStack(Items.COMPASS)), "it should bring the compass to its owner, it holds " + bleep.getMainHandItem());
            helper.assertTrue(bleep.revealed(), "finding it shows you what its favourite is");
        });
    }

    /** As in a real game: its owner holds its favourite (a music disc), it circles, then the owner throws it away. */
    private static void itFetchesAThrownMusicDisc(GameTestHelper helper) {
        Player owner = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(0.5, 0, 1.5));
        owner.snapTo(at.x, at.y, at.z);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MUSIC_DISC_CAT));
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        bleep.setObsession(Obsession.ALL.indexOf(Obsession.ALL.stream().filter(o -> o.id().equals("music_disc")).findFirst().orElseThrow()));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(bleep.revealed(), "holding its favourite should make it circle and show what it is");
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var thrown = helper.spawnItem(Items.MUSIC_DISC_CAT, new Vec3(5.5, 0.2, 1.5));
            thrown.setPickUpDelay(40);
            thrown.setThrower(owner);
        });
        helper.runAfterDelay(41, () -> helper.succeedWhen(() -> helper.assertTrue(owner.getInventory().contains(new ItemStack(Items.MUSIC_DISC_CAT)),
                "it should fetch the thrown disc and bring it back; it holds " + bleep.getMainHandItem() + ", doing " + bleep.activity() + ", at " + helper.relativeVec(bleep.position()))));
    }

    private static void aTorchNeedsAFriend(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        helper.assertFalse(bleep.give(owner, new ItemStack(Items.TORCH)), "a new bleep does not carry a torch yet");
        bleep.setBond(30);
        helper.assertTrue(bleep.give(owner, new ItemStack(Items.TORCH)), "a Friend carries a torch");
        helper.assertTrue(bleep.getOffhandItem().is(Items.TORCH), "in its left hand");
        helper.succeed();
    }

    private static void itsTorchLightsTheWay(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.setBond(30);
        bleep.give(owner, new ItemStack(Items.TORCH));
        helper.runAfterDelay(5, () -> {
            BlockPos light = bleep.light();
            helper.assertTrue(light != null && helper.getLevel().getBlockState(light).is(Blocks.LIGHT), "its torch should light the block over it");
            helper.assertTrue(bleep.takeTorch(owner), "its owner can take the torch back");
            helper.assertTrue(helper.getLevel().getBlockState(light).isAir(), "without the torch the light goes out");
            helper.succeed();
        });
    }

    /** Its owner goes for a monster: a Buddy joins in. (Hitting back for its owner needs real damage, which the test player cannot take.) */
    private static void itFightsForItsOwner(GameTestHelper helper) {
        Player owner = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 0, 1.5));
        owner.snapTo(at.x, at.y, at.z);
        BleepEntity buddy = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        buddy.tame(owner);
        buddy.setBond(60);
        @SuppressWarnings("unchecked")
        EntityType<net.minecraft.world.entity.Mob> zombieType = (EntityType<net.minecraft.world.entity.Mob>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("zombie"));
        var zombie = helper.spawnWithNoFreeWill(zombieType, FAR);
        owner.tickCount = 100; // the test player does not tick: give its hit a moment of its own
        owner.setLastHurtMob(zombie); // its owner hit the zombie
        helper.succeedWhen(() -> helper.assertTrue(buddy.getTarget() == zombie, "a Buddy should go for the zombie its owner hit"));
    }

    /** From Buddy it brings what lies about; what its owner threw away it leaves. */
    private static void aBuddyBringsWhatItFinds(GameTestHelper helper) {
        Player owner = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(0.5, 0, 1.5));
        owner.snapTo(at.x, at.y, at.z);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        bleep.setBond(60);
        bleep.setObsession(COMPASS);
        helper.spawnItem(Items.STRING, new Vec3(5.5, 0.2, 1.5));
        helper.succeedWhen(() -> helper.assertTrue(owner.getInventory().contains(new ItemStack(Items.STRING)), "a Buddy should bring the string to its owner"));
    }

    private static void aBoredBleepStealsACookie(GameTestHelper helper) {
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(player(helper));
        BlockPos chestPos = new BlockPos(6, 0, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getBlockEntity(chestPos, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.COOKIE, 3));
        helper.assertTrue(bleep.beNaughty(NaughtyGoal.Trick.COOKIE), "there is a cookie to steal");
        helper.succeedWhen(() -> helper.assertTrue(chest.getItem(0).getCount() == 2, "it should take one cookie, the chest has " + chest.getItem(0)));
    }

    private static void aBoredBleepMovesATorch(GameTestHelper helper) {
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(player(helper));
        BlockPos torch = new BlockPos(5, 0, 1);
        helper.setBlock(torch, Blocks.TORCH);
        helper.assertTrue(bleep.beNaughty(NaughtyGoal.Trick.TORCH), "there is a torch to move");
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(torch).isAir(), "the torch should be gone from where it stood");
            // it may stand outside the bench, where the tests next door tidy up: look the moment it is put down
            BlockPos moved = bleep.movedTorch();
            helper.assertTrue(moved != null && helper.getLevel().getBlockState(moved).is(Blocks.TORCH), "and stand somewhere else, nothing broken");
        });
    }

    private static void redstoneChargesIt(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.setBattery(50);
        helper.assertTrue(bleep.give(owner, new ItemStack(Items.REDSTONE)), "redstone charges it");
        helper.assertTrue(Math.abs(bleep.battery() - (50 + BleepEntity.REDSTONE_CHARGE)) < 0.01f, "8% a dust, battery is " + bleep.battery());
        helper.assertTrue(bleep.bond() == Bond.GIFT, "and it is still a treat");
        helper.assertTrue(bleep.give(owner, new ItemStack(Items.REDSTONE_BLOCK)), "a block of redstone charges it more");
        helper.assertTrue(bleep.battery() == BleepEntity.FULL, "up to full, battery is " + bleep.battery());
        helper.assertFalse(bleep.give(owner, new ItemStack(Items.REDSTONE_BLOCK)), "a full bleep takes no more");
        helper.succeed();
    }

    private static void redstoneWakesAnEmptyBleep(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.setBattery(0);
        bleep.give(owner, new ItemStack(Items.REDSTONE_BLOCK));
        bleep.give(owner, new ItemStack(Items.REDSTONE_BLOCK));
        helper.succeedWhen(() -> helper.assertFalse(bleep.asleep(), "charged full with redstone, it wakes up"));
    }

    private static void aPowerBankTopsItUp(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.setBattery(30);
        ItemStack bank = new ItemStack(BleepContent.POWER_BANK.get());
        bleep.usePowerBank(owner, bank);
        helper.assertTrue(bleep.battery() == BleepEntity.FULL, "a full bank fills it, battery is " + bleep.battery());
        helper.assertTrue(bank.getDamageValue() == 70, "and has 30 left, it used " + bank.getDamageValue());
        BlockPos home = homebase(helper, HOME);
        helper.getLevel().getBlockState(home).useItemOn(bank, helper.getLevel(), owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(home), Direction.UP, home, false));
        helper.assertTrue(bank.getDamageValue() == 0, "a homebase fills the bank again");
        helper.succeed();
    }

    private static void lightningOverchargesIt(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.setBattery(10);
        @SuppressWarnings("unchecked")
        EntityType<net.minecraft.world.entity.LightningBolt> boltType = (EntityType<net.minecraft.world.entity.LightningBolt>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("lightning_bolt"));
        var bolt = boltType.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        bleep.thunderHit(helper.getLevel(), bolt);
        helper.assertTrue(bleep.battery() == BleepEntity.FULL, "lightning fills it up");
        helper.assertTrue(bleep.isAlive() && bleep.getHealth() == bleep.getMaxHealth(), "and does not hurt it");
        helper.assertTrue(bleep.overcharged(), "it is overcharged");
        helper.succeedWhen(() -> helper.assertTrue(bleep.emotion() == Emotion.GLITCH || bleep.emotion() == Emotion.EXCITED, "hyper and glitchy, it shows " + bleep.emotion()));
    }

    /** A Minecraft item by name: 26.3 and 26.1 keep some (the dyes) in different places. */
    private static ItemStack vanilla(String name) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(name)));
    }

    private static void dyePaintsIt(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        helper.assertTrue(bleep.give(owner, vanilla("red_dye")), "it takes red dye");
        helper.assertTrue(bleep.color() == (net.minecraft.world.item.DyeColor.RED.getTextureDiffuseColor() & 0xFFFFFF), "and turns red");
        helper.assertTrue(bleep.give(owner, vanilla("white_dye")), "white dye");
        helper.assertTrue(bleep.color() == -1, "washes it back to its own white");
        helper.succeed();
    }

    private static void itWearsAHatAndASticker(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.give(owner, new ItemStack(BleepContent.ACCESSORIES.get(Accessory.PARTY_HAT).get()));
        helper.assertTrue(bleep.hat() == Accessory.PARTY_HAT, "it wears the party hat");
        bleep.give(owner, new ItemStack(BleepContent.ACCESSORIES.get(Accessory.TOP_HAT).get()));
        helper.assertTrue(bleep.hat() == Accessory.TOP_HAT, "a new hat takes its place");
        helper.assertTrue(owner.getInventory().contains(new ItemStack(BleepContent.ACCESSORIES.get(Accessory.PARTY_HAT).get())), "and the old one comes back");
        bleep.give(owner, new ItemStack(BleepContent.ACCESSORIES.get(Accessory.STICKER_HEART).get()));
        helper.assertTrue(bleep.sticker() == Accessory.STICKER_HEART && bleep.hat() == Accessory.TOP_HAT, "a sticker goes on next to the hat");
        for (Accessory a : new Accessory[]{Accessory.SUNGLASSES, Accessory.LEATHER_JACKET, Accessory.CAPE, Accessory.BOW_TIE}) bleep.give(owner, new ItemStack(BleepContent.ACCESSORIES.get(a).get()));
        helper.assertTrue(bleep.worn(Accessory.Slot.EYES) == Accessory.SUNGLASSES && bleep.worn(Accessory.Slot.BODY) == Accessory.LEATHER_JACKET
                && bleep.worn(Accessory.Slot.BACK) == Accessory.CAPE && bleep.worn(Accessory.Slot.NECK) == Accessory.BOW_TIE && bleep.hat() == Accessory.TOP_HAT, "sunglasses, a jacket, a cape and a bow tie, all at once");
        bleep.give(owner, new ItemStack(BleepContent.ACCESSORIES.get(Accessory.SCARF).get()));
        helper.assertTrue(bleep.worn(Accessory.Slot.NECK) == Accessory.SCARF && owner.getInventory().contains(new ItemStack(BleepContent.ACCESSORIES.get(Accessory.BOW_TIE).get())), "a scarf takes the bow tie's place");
        helper.assertTrue(bleep.takeOff(owner), "shears take them off");
        helper.assertTrue(bleep.hat() == null && bleep.sticker() == null && bleep.worn(Accessory.Slot.BACK) == null && bleep.worn(Accessory.Slot.EYES) == null, "all of them");
        helper.assertTrue(owner.getInventory().contains(new ItemStack(BleepContent.ACCESSORIES.get(Accessory.CAPE).get())), "the cape comes back too");
        helper.assertTrue(owner.getInventory().contains(new ItemStack(BleepContent.ACCESSORIES.get(Accessory.STICKER_HEART).get())), "and give them back");
        helper.succeed();
    }

    private static void itsNameScrollsAcross(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.setCustomName(Component.literal("Bolt"));
        helper.assertTrue(bleep.display() == Display.NAME, "a new name scrolls across its display");
        helper.succeed();
    }

    private static void anArrowPointsHome(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), FAR);
        bleep.tame(owner);
        BlockPos home = homebase(helper, HOME);
        bleep.setHome(GlobalPos.of(helper.getLevel().dimension(), home));
        bleep.goHome();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(bleep.display() == Display.HOME, "on its way home its display shows the way, it shows " + bleep.display());
            helper.succeed();
        });
    }

    private static void itsDiaryStartsTheDayItWasBuilt(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.boot();
        var diary = bleep.memory().diary;
        helper.assertTrue(!diary.isEmpty() && diary.getFirst().key().equals("diary.bleep.born") && diary.getFirst().day() == 1, "its diary starts on day 1, the day it was built");
        helper.assertTrue(bleep.news(), "and that is news for its owner");
        helper.succeed();
    }

    private static void aNewMobGoesInTheBleepedia(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        @SuppressWarnings("unchecked")
        EntityType<net.minecraft.world.entity.Mob> cow = (EntityType<net.minecraft.world.entity.Mob>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("cow"));
        helper.spawnWithNoFreeWill(cow, FAR);
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.memory().mobs.containsKey("minecraft:cow"), "the cow should be in its Bleepedia");
            helper.assertTrue(bleep.news(), "with a ! for its owner");
            helper.assertTrue(bleep.memory().diary.stream().anyMatch(e -> e.key().equals("diary.bleep.first_mob")), "and its first creature in its diary");
        });
    }

    private static void aSpecialBlockIsAFind(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        helper.setBlock(new BlockPos(5, 0, 1), Blocks.BEACON);
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.memory().finds.containsKey("minecraft:beacon"), "a beacon should be a find");
            helper.assertTrue(bleep.memory().diary.stream().anyMatch(e -> e.key().equals("diary.bleep.find")), "written in its diary");
        });
    }

    private static void openingTheBleepediaReadsTheNews(GameTestHelper helper) {
        Player owner = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 0, 1.5));
        owner.snapTo(at.x, at.y, at.z);
        BleepEntity bleep = bleep(helper, owner);
        bleep.boot();
        helper.assertTrue(bleep.news(), "something new to read");
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.OPEN_BOOK, 1), "its owner opens the Bleepedia");
        helper.assertFalse(bleep.news(), "the news is read");
        helper.assertTrue(bleep.memory().lastOpened == helper.getLevel().getGameTime(), "and it remembers when");
        helper.succeed();
    }

    private static void aNameAlwaysShows(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.setCustomName(Component.literal("Bolt"));
        helper.assertTrue(bleep.isCustomNameVisible(), "a bleep's name should always show, like a player's");
        helper.succeed();
    }

    /** A test player with a core in their hand. */
    private static void itDancesToMusic(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        helper.setBlock(new BlockPos(6, 0, 1), Blocks.JUKEBOX.defaultBlockState().setValue(net.minecraft.world.level.block.JukeboxBlock.HAS_RECORD, true));
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.dancing() && bleep.activity() == Activity.DANCING, "with a record playing close by it should dance");
            helper.assertTrue(bleep.memory().diary.stream().anyMatch(e -> e.key().equals("diary.bleep.dance")), "its first dance goes in its diary");
        });
    }

    private static void itSleepsByItsOwnersBed(GameTestHelper helper) {
        Player owner = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 0, 1.5));
        owner.snapTo(at.x, at.y, at.z);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        BlockPos bed = new BlockPos(1, 0, 1);
        owner.setSleepingPos(helper.absolutePos(bed)); // in bed (a real bed needs a real night)
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.activity() == Activity.BEDSIDE && bleep.emotion() == Emotion.ASLEEP, "its owner asleep: it sleeps by the bed, it is " + bleep.activity());
            helper.assertTrue(bleep.memory().diary.stream().anyMatch(e -> e.key().equals("diary.bleep.bedside")), "written in its diary");
        });
    }

    private static void itDigsSomethingUp(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        helper.assertTrue(bleep.dig(), "a free bleep may dig");
        helper.succeedWhen(() -> helper.assertFalse(bleep.getMainHandItem().isEmpty(), "and it should come up with something"));
    }

    private static void anIronGolemIsItsBigBrother(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        @SuppressWarnings("unchecked")
        EntityType<net.minecraft.world.entity.Mob> golem = (EntityType<net.minecraft.world.entity.Mob>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("iron_golem"));
        helper.spawnWithNoFreeWill(golem, FAR);
        helper.succeedWhen(() -> helper.assertTrue(bleep.memory().diary.stream().anyMatch(e -> e.key().equals("diary.bleep.golem")), "meeting a golem goes in its diary"));
    }

    private static void anAbandonedBleepWakesWithRedstone(GameTestHelper helper) {
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        io.hookdev.bleep.entity.Abandoned.setUp(bleep, helper.getLevel().getRandom());
        helper.assertTrue(bleep.abandoned() && bleep.getOwner() == null && bleep.hasCustomName(), "an abandoned bleep is nobody's and has an old name");
        Player player = player(helper);
        bleep.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(bleep.abandoned(), "an empty hand does not wake it");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.REDSTONE_BLOCK));
        bleep.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertFalse(bleep.abandoned(), "a block of redstone wakes it");
        helper.assertTrue(bleep.isOwnedBy(player) && bleep.battery() == BleepEntity.FULL, "it is the player's now, fully charged");
        helper.assertTrue(bleep.memory().diary.stream().anyMatch(e -> e.key().startsWith("diary.bleep.past.")), "with its past in its diary");
        helper.succeed();
    }

    private static void itIsCalledBleepUntilYouRenameIt(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(bleep.hasCustomName() && bleep.getCustomName().getString().equals("Bleep") && bleep.isCustomNameVisible(), "a new bleep is called Bleep, above its head");
            helper.assertTrue(io.hookdev.bleep.network.BleepRename.rename(owner, bleep, "  Sparky "), "its owner renames it");
            helper.assertTrue(bleep.getCustomName().getString().equals("Sparky"), "to Sparky, it is " + bleep.getCustomName().getString());
            io.hookdev.bleep.network.BleepRename.rename(owner, bleep, "   ");
            helper.assertTrue(bleep.getCustomName().getString().equals("Bleep"), "an empty name makes it Bleep again");
            helper.assertFalse(io.hookdev.bleep.network.BleepRename.rename(player(helper), bleep, "Thief"), "someone else cannot rename it");
            helper.succeed();
        });
    }

    private static void aNameTagDoesNotStick(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Zap"));
        owner.setItemInHand(InteractionHand.MAIN_HAND, tag);
        bleep.interact(owner, InteractionHand.MAIN_HAND, Vec3.ZERO);
        helper.assertTrue(tag.getCount() == 1, "the name tag is not used up");
        helper.succeedWhen(() -> helper.assertTrue(bleep.getCustomName() != null && bleep.getCustomName().getString().equals("Bleep"), "and it is still called Bleep"));
    }

    private static void itsMagnetFillsItsStorage(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.setMagnet(true);
        helper.spawnItem(Items.STRING, new Vec3(6.5, 0.2, 1.5));
        helper.succeedWhen(() -> helper.assertTrue(bleep.storage().countItem(Items.STRING) == 1, "its magnet should pull the string into its storage"));
    }

    private static void itFightsWithASwordFromItsStorage(GameTestHelper helper) {
        Player owner = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 0, 1.5));
        owner.snapTo(at.x, at.y, at.z);
        BleepEntity buddy = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        buddy.tame(owner);
        buddy.setBond(60);
        buddy.storage().addItem(new ItemStack(Items.STICK));
        buddy.storage().addItem(new ItemStack(Items.WOODEN_SWORD));
        buddy.storage().addItem(new ItemStack(Items.IRON_SWORD));
        @SuppressWarnings("unchecked")
        EntityType<net.minecraft.world.entity.Mob> zombieType = (EntityType<net.minecraft.world.entity.Mob>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("zombie"));
        var zombie = helper.spawnWithNoFreeWill(zombieType, FAR);
        owner.tickCount = 100;
        owner.setLastHurtMob(zombie);
        helper.succeedWhen(() -> {
            helper.assertTrue(buddy.armed() && buddy.getMainHandItem().is(Items.IRON_SWORD), "it should take its best sword out to fight, it holds " + buddy.getMainHandItem());
            helper.assertTrue(buddy.storage().countItem(Items.IRON_SWORD) == 0 && buddy.storage().countItem(Items.WOODEN_SWORD) == 1, "the iron one, out of its storage");
        });
    }

    private static void itShowsOffAndKeepsIt(GameTestHelper helper) {
        Player owner = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 0, 1.5));
        owner.snapTo(at.x, at.y, at.z);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        bleep.storage().addItem(new ItemStack(Items.DIAMOND));
        helper.assertTrue(bleep.showOff(), "it takes something out to show");
        helper.assertTrue(bleep.getMainHandItem().is(Items.DIAMOND) && bleep.storage().isEmpty(), "the diamond, in its hand");
        helper.runAfterDelay(100, () -> {
            helper.assertFalse(owner.getInventory().contains(new ItemStack(Items.DIAMOND)), "showing is not giving: its owner does not get it");
            helper.assertTrue(bleep.storage().countItem(Items.DIAMOND) == 1 && bleep.getMainHandItem().isEmpty(), "after showing it, it goes back in its storage");
            helper.succeed();
        });
    }

    /** A player in the bench, in the level (so its bleeps can find it). */
    private static Player owner(GameTestHelper helper, double x) {
        Player owner = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(x, 0, 1.5));
        owner.snapTo(at.x, at.y, at.z);
        return owner;
    }

    private static void itBringsTheBallBack(GameTestHelper helper) {
        Player owner = owner(helper, 0.5);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        int bond = bleep.bond();
        helper.spawnItem(BleepContent.BALL.get(), new Vec3(6.5, 0.2, 1.5));
        helper.succeedWhen(() -> {
            helper.assertTrue(owner.getInventory().contains(new ItemStack(BleepContent.BALL.get())), "it should bring the ball back");
            helper.assertTrue(bleep.bond() == bond + Bond.PLAY, "playing ball raises the bond, it is " + bleep.bond() + ", diary " + bleep.memory().diary.stream().map(e -> e.key()).toList());
            helper.assertTrue(bleep.storage().isEmpty(), "and the magnet never takes a ball");
        });
    }

    private static void twoBleepsPlayTogether(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity one = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        BleepEntity two = helper.spawn(BleepContent.BLEEP.get(), FAR);
        one.tame(owner);
        two.tame(owner);
        helper.assertTrue(one.playWith(two), "two free bleeps play together");
        helper.succeedWhen(() -> {
            helper.assertTrue(one.activity() == Activity.PLAYING && two.activity() == Activity.PLAYING, "both should be playing, they are " + one.activity() + " and " + two.activity());
            helper.assertTrue(one.memory().diary.stream().anyMatch(e -> e.key().equals("diary.bleep.friend")), "a new friend in its diary");
        });
    }

    private static void pettingOneMakesTheOtherJealous(GameTestHelper helper) {
        Player owner = owner(helper, 1.5);
        BleepEntity petted = helper.spawnWithNoFreeWill(BleepContent.BLEEP.get(), SPOT);
        BleepEntity other = helper.spawnWithNoFreeWill(BleepContent.BLEEP.get(), FAR);
        petted.tame(owner);
        other.tame(owner);
        petted.pet();
        helper.succeedWhen(() -> {
            helper.assertTrue(other.emotion() == Emotion.SAD, "the other one should be sad, it is " + other.emotion());
            helper.assertTrue(other.thought().startsWith("thought.bleep.together.jealous."), "and jealous, it thinks " + other.thought());
        });
    }

    private static void aHappyBleepCheersUpASadOne(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity happy = helper.spawnWithNoFreeWill(BleepContent.BLEEP.get(), SPOT);
        BleepEntity sad = helper.spawnWithNoFreeWill(BleepContent.BLEEP.get(), FAR);
        happy.tame(owner);
        sad.tame(owner);
        happy.feel(Emotion.EXCITED, 400);
        sad.feel(Emotion.SAD, 400);
        helper.succeedWhen(() -> helper.assertTrue(sad.thought().equals("thought.bleep.together.cheered") && sad.emotion() == Emotion.HAPPY, "the sad one should cheer up, it is " + sad.emotion()));
    }

    private static void llamaSpitMakesItGlitch(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        var spit = new net.minecraft.world.entity.projectile.LlamaSpit(
                (EntityType<net.minecraft.world.entity.projectile.LlamaSpit>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("llama_spit")), helper.getLevel());
        bleep.hurtServer(helper.getLevel(), helper.getLevel().damageSources().spit(spit, null), 1);
        helper.assertTrue(bleep.thought().equals("thought.bleep.spit"), "spit in its circuits, it thinks " + bleep.thought());
        helper.succeedWhen(() -> helper.assertTrue(bleep.emotion() == Emotion.GLITCH, "its screen should glitch, it shows " + bleep.emotion()));
    }

    private static void aBedByItsHomeIsAComfort(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        BlockPos home = homebase(helper, HOME);
        bleep.setHome(GlobalPos.of(helper.getLevel().dimension(), home));
        helper.setBlock(new BlockPos(1, 0, 2), net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("red_bed")));
        bleep.checkComforts();
        helper.assertTrue((bleep.comforts() & BleepEntity.COMFORT_BED) != 0, "a bed by its home is a comfort");
        helper.assertTrue(bleep.memory().diary.stream().anyMatch(e -> e.key().equals("diary.bleep.home_bed")), "written in its diary");
        helper.succeed();
    }

    private static void tenBleepsIsPlenty(GameTestHelper helper) {
        Player player = withCore(helper);
        for (Item part : BleepCoreItem.parts()) player.getInventory().add(new ItemStack(part));
        var owners = io.hookdev.bleep.BleepOwners.get(helper.getLevel());
        for (int i = 0; i < io.hookdev.bleep.BleepOwners.MAX; i++) owners.add(player.getUUID(), java.util.UUID.randomUUID());
        useCore(helper, player);
        helper.assertTrue(player.getMainHandItem().is(BleepContent.BLEEP_CORE.get()), "with ten bleeps the core stays in your hand");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(BleepEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(SPOT)).inflate(8)).isEmpty(), "no eleventh bleep");
        helper.succeed();
    }

    /** Two bleeps of one owner, side by side. */
    private static BleepEntity[] pair(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity one = helper.spawn(BleepContent.BLEEP.get(), SPOT), two = helper.spawn(BleepContent.BLEEP.get(), FAR);
        one.tame(owner);
        two.tame(owner);
        return new BleepEntity[] {one, two};
    }

    private static void twoBleepsSayHi(GameTestHelper helper) {
        BleepEntity[] b = pair(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(b[0].friendship(b[1]) >= 1 && b[1].friendship(b[0]) >= 1, "meeting, they say hi and know each other now");
            helper.assertTrue(b[0].memory().diary.stream().anyMatch(e -> e.key().equals("diary.bleep.greet")), "written in its diary");
        });
    }

    private static void twoBleepsHaveAChat(GameTestHelper helper) {
        BleepEntity[] b = pair(helper);
        helper.runAfterDelay(80, () -> helper.assertTrue(b[0].startChat(b[1], 4), "two bleeps start a chat")); // after saying hi
        helper.succeedWhen(() -> {
            // said hi (+1) and had the whole chat (+1); what it thinks last may be a hi from a bleep next door
            helper.assertTrue(!b[0].talking() && b[0].friendship(b[1]) >= 2 && b[1].friendship(b[0]) >= 2, "after the chat they are better friends, " + b[0].friendship(b[1]));
        });
    }

    private static void tagHasOneWhoIsIt(GameTestHelper helper) {
        BleepEntity[] b = pair(helper);
        helper.assertTrue(b[0].playWith(b[1]), "they play tag");
        helper.assertTrue(b[0].it() && !b[1].it(), "the one who started is it");
        helper.succeed();
    }

    private static void bleepsFightWithThunderClouds(GameTestHelper helper) {
        BleepEntity[] b = pair(helper);
        helper.assertTrue(b[0].startFight(b[1]), "they start a fight");
        helper.assertTrue(b[0].friendship(b[1]) < 0, "fighting is bad for a friendship");
        helper.succeedWhen(() -> {
            helper.assertTrue(b[1].battery() < BleepEntity.FULL || b[0].battery() < BleepEntity.FULL, "a hit costs a little battery");
            helper.assertTrue(b[0].activity() == Activity.SQUABBLING || !b[0].fighting(), "squabbling, it is " + b[0].activity());
        });
    }

    private static void pettingStopsAFight(GameTestHelper helper) {
        BleepEntity[] b = pair(helper);
        b[0].startFight(b[1]);
        b[0].pet();
        helper.assertFalse(b[0].fighting() || b[1].fighting(), "petting one breaks it up");
        helper.succeed();
    }

    private static void twoFightsMakeRivals(GameTestHelper helper) {
        BleepEntity[] b = pair(helper);
        b[0].startFight(b[1]);
        b[0].pet();
        b[0].startFight(b[1]);
        b[0].pet();
        var friends = io.hookdev.bleep.network.BleepediaData.of(b[0]).friends();
        helper.assertTrue(friends.size() == 1 && friends.getFirst().startsWith("rival\t"), "two fights: rivals, in its Bleepedia, it has " + friends);
        helper.assertTrue(b[0].memory().diary.stream().anyMatch(e -> e.key().equals("diary.bleep.rival")), "and in its diary");
        helper.succeed();
    }

    private static void stayingBleepsStillChat(GameTestHelper helper) {
        BleepEntity[] b = pair(helper);
        b[0].stay();
        b[1].stay();
        helper.assertTrue(b[0].stayAt() != null && !b[0].parked(), "staying, it has a spot to potter about near, and is not parked");
        helper.runAfterDelay(30, () -> helper.assertTrue(b[0].startChat(b[1], 1), "staying bleeps chat too"));
        helper.succeedWhen(() -> helper.assertTrue(b[0].activity() == Activity.CHATTING, "chatting while staying, it is " + b[0].activity()));
    }

    private static void itWelcomesItsOwnerBack(GameTestHelper helper) {
        Player owner = owner(helper, 0.5);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), FAR);
        bleep.tame(owner);
        bleep.missedOwner(BleepEntity.WELCOME_AFTER + 100);
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.welcoming() && bleep.activity() == Activity.WELCOMING, "its owner back after a long time: it should be over the moon, it is " + bleep.activity());
            helper.assertTrue(bleep.memory().diary.stream().anyMatch(e -> e.key().equals("diary.bleep.welcome")), "written in its diary");
        });
    }

    private static void aBirthdayWithCake(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        helper.assertTrue(BleepEntity.birthdayOn(31) && BleepEntity.birthdayOn(61) && !BleepEntity.birthdayOn(30) && !BleepEntity.birthdayOn(1), "a birthday every thirty days, from day 31");
        bleep.birthday(31); // a new test world is too young for a real one
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.party(), "thirty days old: a birthday party");
            helper.assertTrue(bleep.memory().diary.stream().anyMatch(e -> e.key().equals("diary.bleep.birthday") && e.arg().equals("30")), "30 days old, in its diary");
            int bond = bleep.bond();
            helper.assertTrue(bleep.give(owner, new ItemStack(Items.CAKE)), "a birthday cake is very welcome");
            helper.assertTrue(bleep.bond() > bond, "and raises the bond");
        });
    }

    private static void itDoesTricksItKnows(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        helper.assertFalse(bleep.doTrick(io.hookdev.bleep.entity.Trick.PLAY_DEAD), "a new bleep cannot play dead yet");
        helper.assertTrue(bleep.doTrick(io.hookdev.bleep.entity.Trick.SPIN), "but it can spin");
        int bond = bleep.bond();
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.trick() == null && bleep.bond() > bond, "a trick done raises the bond");
        });
    }

    private static void itHides(GameTestHelper helper) {
        Player owner = owner(helper, 1.5);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        // the test world is too small to hide in for real: if it finds a spot, it is off to it
        boolean hides = BleepCommand.obey(owner, bleep, BleepCommand.HIDE, 1);
        helper.assertTrue(hides == (bleep.hideMode() == BleepEntity.HIDE_GOING), "hiding means going to its spot");
        helper.assertFalse(BleepCommand.obey(player(helper), bleep, BleepCommand.HIDE, 1) && !hides, "only its owner starts a game");
        helper.succeed();
    }

    private static void itCountsThenSeeks(GameTestHelper helper) {
        Player owner = owner(helper, 1.5);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        helper.assertTrue(bleep.startSeeking(), "it counts to ten");
        helper.runAfterDelay(20, () -> helper.assertTrue(bleep.emotion() == Emotion.ASLEEP, "with its eyes shut"));
        helper.succeedWhen(() -> helper.assertTrue(bleep.hideMode() == 0 && bleep.memory().diary.stream().anyMatch(e -> e.key().equals("diary.bleep.hide_and_seek")), "then it finds its owner close by, it thinks " + bleep.thought()));
    }

    private static void aFarmerHarvestsAndReplants(GameTestHelper helper) {
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(player(helper));
        BlockPos crop = new BlockPos(5, 0, 1);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        var wheat = (net.minecraft.world.level.block.CropBlock) Blocks.WHEAT;
        helper.setBlock(crop, wheat.getStateForAge(wheat.getMaxAge()));
        bleep.stay();
        bleep.toggleJob(BleepEntity.JOB_FARMER);
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.storage().countItem(Items.WHEAT) >= 1, "the wheat should be in its storage");
            helper.assertTrue(helper.getBlockState(crop).is(Blocks.WHEAT) && !wheat.isMaxAge(helper.getBlockState(crop)), "and the wheat planted again");
        });
    }

    private static void tidyUpPutsItsStorageInAChest(GameTestHelper helper) {
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(player(helper));
        BlockPos chestPos = new BlockPos(5, 0, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getBlockEntity(chestPos, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
        bleep.storage().addItem(new ItemStack(Items.STRING, 5));
        bleep.storage().addItem(new ItemStack(Items.IRON_SWORD));
        bleep.stay();
        bleep.toggleJob(BleepEntity.JOB_TIDY);
        helper.succeedWhen(() -> {
            helper.assertTrue(chest.countItem(Items.STRING) == 5, "the string should be in the chest");
            helper.assertTrue(bleep.storage().countItem(Items.IRON_SWORD) == 1, "it keeps its sword");
        });
    }

    private static void theStayRadiusCanChange(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        bleep.stay();
        helper.assertTrue(BleepCommand.obey(owner, bleep, BleepCommand.RADIUS + 10, 1) && bleep.stayRadius() == 10, "the radius is 10 now");
        BleepCommand.obey(owner, bleep, BleepCommand.RADIUS + 99, 1);
        helper.assertTrue(bleep.stayRadius() == 10, "a radius out of range is ignored");
        bleep.setStayRadius(1);
        helper.assertTrue(bleep.stayRadius() == BleepEntity.MIN_RADIUS, "two at least");
        helper.succeed();
    }

    private static void chipsAreInstalledForGood(GameTestHelper helper) {
        Player owner = player(helper);
        BleepEntity bleep = bleep(helper, owner);
        double speed = bleep.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        ItemStack chip = new ItemStack(BleepContent.CHIPS.get(io.hookdev.bleep.entity.Chip.SPEED).get());
        helper.assertTrue(bleep.give(owner, chip), "a chip is installed");
        helper.assertTrue(bleep.has(io.hookdev.bleep.entity.Chip.SPEED) && bleep.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) > speed, "and it is faster");
        helper.assertFalse(bleep.give(owner, chip), "one of each kind");
        bleep.give(owner, new ItemStack(BleepContent.CHIPS.get(io.hookdev.bleep.entity.Chip.SHIELD).get()));
        helper.assertTrue(io.hookdev.bleep.network.BleepediaData.of(bleep).chips() == 3, "its chips are in its Bleepedia");
        helper.assertTrue(bleep.uninstall(io.hookdev.bleep.entity.Chip.SPEED, owner) && !bleep.has(io.hookdev.bleep.entity.Chip.SPEED), "a chip can come out again");
        helper.assertTrue(Math.abs(bleep.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) - speed) < 1e-6, "and it is back to its own speed");
        helper.assertTrue(owner.getInventory().contains(s -> s.is(BleepContent.CHIPS.get(io.hookdev.bleep.entity.Chip.SPEED).get())), "its owner gets the chip back");
        helper.succeed();
    }

    private static void itWritesALetter(GameTestHelper helper) {
        Player owner = owner(helper, 0.5);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), FAR);
        bleep.tame(owner);
        bleep.boot();
        bleep.memory().write(helper.getLevel().getGameTime(), "diary.bleep.llama", "");
        helper.assertTrue(bleep.writeLetter(), "it writes a letter");
        var book = bleep.getMainHandItem().get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
        helper.assertTrue(book != null && book.author().equals(bleep.getName().getString()), "a written book, signed by it");
        helper.succeedWhen(() -> helper.assertTrue(owner.getInventory().contains(s -> s.is(Items.WRITTEN_BOOK)), "and it brings it over"));
    }

    private static void itSawAStarFall(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.sawStarFall(helper.absolutePos(FAR));
        helper.assertTrue(bleep.memory().signs == 3 && bleep.news(), "a star fell: three strange signs in its Bleepedia, and news");
        helper.assertTrue(io.hookdev.bleep.network.BleepediaData.of(bleep).signs() == 3, "sent with its Bleepedia");
        helper.assertTrue(bleep.memory().diary.stream().anyMatch(e -> e.key().equals("diary.bleep.star")), "and in its diary");
        for (int i = 0; i < 40; i++) bleep.addSigns(3);
        helper.assertTrue(bleep.memory().signs == io.hookdev.bleep.entity.Memory.SIGNS, "never more than 64");
        helper.succeed();
    }

    private static void aFallenStarHoldsShards(GameTestHelper helper) {
        // its loot, worked out directly (in the bench a magnet next door might take what falls)
        int least = Integer.MAX_VALUE, cores = 0;
        for (int i = 0; i < 40; i++) {
            var drops = net.minecraft.world.level.block.Block.getDrops(BleepContent.FALLEN_STAR.get().defaultBlockState(), helper.getLevel(), helper.absolutePos(FAR), null);
            least = Math.min(least, drops.stream().filter(d -> d.is(BleepContent.STAR_SHARD.get())).mapToInt(ItemStack::getCount).sum());
            if (drops.stream().anyMatch(d -> d.is(BleepContent.BLEEP_CORE.get()))) cores++;
        }
        helper.assertTrue(least >= 3, "a fallen star always gives three to five star shards, once only " + least);
        helper.assertTrue(cores > 5 && cores < 35, "and about half the time a Bleep Core: " + cores + " in 40");
        helper.succeed();
    }

    private static void aHomebaseDropsItself(GameTestHelper helper) {
        var drops = net.minecraft.world.level.block.Block.getDrops(BleepContent.HOMEBASE.get().defaultBlockState(), helper.getLevel(), helper.absolutePos(HOME), null);
        helper.assertTrue(drops.stream().anyMatch(d -> d.is(BleepContent.HOMEBASE_ITEM.get())), "a broken homebase drops itself (26.1 and 26.3 read its loot table), it gave " + drops);
        helper.succeed();
    }

    private static void itGuardsYourThings(GameTestHelper helper) {
        Player owner = owner(helper, 0.5);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), FAR);
        bleep.tame(owner);
        helper.spawnItem(Items.DIAMOND, new Vec3(5.5, 0.2, 1.5));
        bleep.guardGrave(helper.absolutePos(FAR));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(bleep.kept().stream().anyMatch(s -> s.is(Items.DIAMOND)) && bleep.activity() == Activity.GUARDING, "it keeps the diamond safe at the grave");
            Vec3 near = helper.absoluteVec(new Vec3(5.5, 0, 1.5));
            owner.snapTo(near.x, near.y, near.z); // its owner is back
        });
        helper.succeedWhen(() -> helper.assertTrue(owner.getInventory().contains(new ItemStack(Items.DIAMOND)) && bleep.kept().isEmpty() && bleep.guarding() == null, "and gives it back"));
    }

    private static void itBringsFoodWhenYouAreHurt(GameTestHelper helper) {
        Player owner = owner(helper, 0.5);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), FAR);
        bleep.tame(owner);
        bleep.storage().addItem(new ItemStack(Items.BREAD, 3));
        owner.setHealth(5);
        helper.succeedWhen(() -> helper.assertTrue(owner.getInventory().contains(new ItemStack(Items.BREAD)) && bleep.storage().countItem(Items.BREAD) == 2, "it brings one bread to its hurt owner"));
    }

    private static void itWarnsForACreeper(GameTestHelper helper) {
        Player owner = owner(helper, 1.5);
        BleepEntity bleep = helper.spawnWithNoFreeWill(BleepContent.BLEEP.get(), FAR);
        bleep.tame(owner);
        @SuppressWarnings("unchecked")
        EntityType<net.minecraft.world.entity.Mob> creeper = (EntityType<net.minecraft.world.entity.Mob>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("creeper"));
        helper.spawnWithNoFreeWill(creeper, new BlockPos(2, 0, 1));
        helper.succeedWhen(() -> helper.assertTrue(bleep.display() == io.hookdev.bleep.entity.Display.ALERT, "a creeper right by its owner: a big red ! on its screen, it shows " + bleep.display()));
    }

    /** A bleep with free will, staying at SPOT with this job on. */
    private static BleepEntity worker(GameTestHelper helper, int job) {
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(player(helper));
        bleep.stay();
        bleep.toggleJob(job);
        return bleep;
    }

    private static void aFisherCatchesSomething(GameTestHelper helper) {
        helper.setBlock(new BlockPos(6, -1, 1), Blocks.WATER);
        BleepEntity bleep = worker(helper, BleepEntity.JOB_FISHER);
        helper.succeedWhen(() -> helper.assertFalse(bleep.storage().isEmpty(), "it should fish something out of the water"));
    }

    private static void aSmelterFillsTheFurnace(GameTestHelper helper) {
        BlockPos at = new BlockPos(5, 0, 1);
        helper.setBlock(at, Blocks.FURNACE);
        BleepEntity bleep = worker(helper, BleepEntity.JOB_SMELTER);
        bleep.storage().addItem(new ItemStack(Items.RAW_IRON, 2));
        bleep.storage().addItem(new ItemStack(Items.COAL, 1));
        helper.succeedWhen(() -> helper.assertTrue(bleep.storage().countItem(Items.RAW_IRON) == 0 && bleep.storage().countItem(Items.COAL) == 0, "the raw iron and the coal go in the furnace"));
    }

    private static void aGardenerPlantsFlowers(GameTestHelper helper) {
        BlockPos bed = new BlockPos(5, 0, 1);
        helper.setBlock(bed.below(), Blocks.GRASS_BLOCK);
        BleepEntity bleep = worker(helper, BleepEntity.JOB_GARDENER);
        bleep.storage().addItem(new ItemStack(Items.POPPY, 2));
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(bed).is(Blocks.POPPY), "a poppy should grow in its garden"));
    }

    private static void whatItDoesMostBecomesItsHobby(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        for (int i = 0; i < BleepEntity.HOBBY_AT - 1; i++) bleep.hobbyPoint("digging");
        helper.assertTrue(bleep.memory().hobby.isEmpty(), "not quite a hobby yet");
        bleep.hobbyPoint("digging");
        helper.assertTrue(bleep.hobby("digging"), "fifteen digs: digging is its hobby");
        for (int i = 0; i < BleepEntity.HOBBY_AT + 5; i++) bleep.hobbyPoint("dancing");
        helper.assertTrue(bleep.hobby("digging"), "a new one has to lead clearly before it takes over");
        for (int i = 0; i < 6; i++) bleep.hobbyPoint("dancing");
        helper.assertTrue(bleep.hobby("dancing"), "and then it does");
        helper.assertTrue(io.hookdev.bleep.network.BleepediaData.of(bleep).hobby().equals("dancing"), "its hobby, in its Bleepedia");
        helper.succeed();
    }

    private static void aKilometreIsAMilestone(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.memory().distance = 1200;
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.memory().milestones.containsKey("drive_1k") && bleep.news(), "a kilometre driven: a badge, and news");
            helper.assertFalse(bleep.memory().milestones.containsKey("drive_10k"), "not ten yet");
            helper.assertTrue(io.hookdev.bleep.network.BleepediaData.of(bleep).milestones().containsKey("drive_1k"), "in its Bleepedia");
        });
    }

    /** A vanilla entity type by name (26.1 and 26.3 keep them in different classes). */
    @SuppressWarnings("unchecked")
    private static <E extends net.minecraft.world.entity.Entity> net.minecraft.world.entity.EntityType<E> type(String name) {
        return (net.minecraft.world.entity.EntityType<E>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(name));
    }

    private static void itShowsTheWay(GameTestHelper helper) {
        Player owner = owner(helper, 5.5);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), FAR);
        bleep.tame(owner);
        BlockPos home = homebase(helper, HOME);
        bleep.setHome(GlobalPos.of(helper.getLevel().dimension(), home));
        helper.runAfterDelay(10, () -> helper.assertTrue(bleep.showTheWay(), "it knows the way home"));
        helper.succeedWhen(() -> {
            helper.assertTrue(bleep.wayTo() != null, "it leads the way");
            helper.assertTrue(bleep.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(home)) < 2.5 * 2.5, "and drives ahead to its homebase");
        });
    }

    private static void itDreams(GameTestHelper helper) {
        BleepEntity bleep = bleep(helper, player(helper));
        bleep.dreamNow();
        helper.assertTrue(bleep.display() == io.hookdev.bleep.entity.Display.DREAM && !bleep.dream().isEmpty(), "a dream on its screen");
        helper.assertTrue(bleep.memory().diary.stream().anyMatch(e -> e.key().startsWith("diary.bleep.dream.") && !e.key().endsWith(".first")), "and in its diary");
        int lines = bleep.memory().diary.size();
        bleep.dreamNow();
        helper.assertTrue(bleep.memory().diary.size() == lines, "one dream a night goes in its diary");
        helper.succeed();
    }

    private static void itBuriesATreasure(GameTestHelper helper) {
        for (int x = 0; x < 7; x++) for (int z = 0; z < 3; z++) helper.setBlock(new BlockPos(x, -1, z), Blocks.DIRT);
        Player owner = owner(helper, 0.5);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        helper.assertFalse(bleep.startBurying(), "an empty storage: nothing to bury");
        bleep.storage().addItem(new ItemStack(Items.DIAMOND, 3));
        boolean[] dug = {false};
        helper.runAfterDelay(20, () -> helper.assertTrue(bleep.startBurying(), "it goes off to bury something (on the ground: it plans a route)"));
        helper.succeedWhen(() -> {
            if (!dug[0] && bleep.treasureMode() == BleepEntity.BURIED) {
                helper.assertTrue(bleep.storage().countItem(Items.DIAMOND) == 2, "one diamond, buried");
                dug[0] = bleep.dugUp(owner, bleep.treasure());
            }
            helper.assertTrue(dug[0], "buried: its owner digs right there, it is " + bleep.treasureMode());
            helper.assertItemEntityPresent(Items.DIAMOND);
            helper.assertTrue(bleep.treasure() == null && bleep.treasureMode() == 0, "and the hunt is over");
        });
    }

    private static void itFindsWhatYouHid(GameTestHelper helper) {
        Player owner = owner(helper, 0.5);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        helper.assertTrue(bleep.startFinding(), "it shuts its eyes");
        bleep.sawHidden(helper.spawnItem(Items.APPLE, new Vec3(6.5, 0.2, 1.5))); // its owner tossed an apple over there
        helper.succeedWhen(() -> helper.assertTrue(owner.getInventory().contains(new ItemStack(Items.APPLE)), "it finds the apple and brings it back"));
    }

    private static net.minecraft.world.entity.npc.villager.Villager shop(GameTestHelper helper, boolean stocked) {
        net.minecraft.world.entity.npc.villager.Villager villager = helper.spawnWithNoFreeWill(type("villager"), FAR);
        var offers = new net.minecraft.world.item.trading.MerchantOffers();
        if (stocked) {
            offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 3), new ItemStack(Items.BOOK), 12, 1, 0.05f));
            offers.add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 30), new ItemStack(Items.DIAMOND), 12, 1, 0.05f)); // too dear
        }
        villager.setVillagerData(villager.getVillagerData().withProfession(helper.getLevel().registryAccess(), net.minecraft.world.entity.npc.villager.VillagerProfession.LIBRARIAN).withLevel(2)); // an unemployed one has no trades
        villager.setOffers(offers);
        return villager;
    }

    private static void itGoesShopping(GameTestHelper helper) {
        Player owner = owner(helper, 0.5);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        shop(helper, true);
        helper.assertTrue(bleep.give(owner, new ItemStack(Items.EMERALD, 5)), "it takes the emeralds");
        helper.assertTrue(bleep.shopping(), "and goes off shopping");
        helper.succeedWhen(() -> {
            helper.assertTrue(owner.getInventory().contains(new ItemStack(Items.BOOK)), "it buys the book (it cannot afford the diamond)");
            helper.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 2, "and brings back the two emeralds it did not spend");
        });
    }

    private static void itBringsTheEmeraldsBack(GameTestHelper helper) {
        Player owner = owner(helper, 0.5);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        shop(helper, false);
        helper.assertTrue(bleep.give(owner, new ItemStack(Items.EMERALD, 4)), "it takes the emeralds");
        helper.succeedWhen(() -> helper.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 4, "nothing to buy: all four come back"));
    }

    private static void itRidesAlong(GameTestHelper helper) {
        Player owner = owner(helper, 0.5);
        BleepEntity bleep = helper.spawn(BleepContent.BLEEP.get(), SPOT);
        bleep.tame(owner);
        net.minecraft.world.entity.Entity ownCart = helper.spawn(type("minecart"), HOME);
        helper.spawn(type("minecart"), FAR);
        owner.startRiding(ownCart);
        helper.succeedWhen(() -> helper.assertTrue(bleep.getVehicle() instanceof net.minecraft.world.entity.vehicle.minecart.AbstractMinecart cart && cart != ownCart,
                "its owner rides a minecart: it hops in the empty one"));
    }

    private static void itReactsToWhatYouDo(GameTestHelper helper) {
        Player owner = owner(helper, 1.5);
        BleepEntity bleep = helper.spawnWithNoFreeWill(BleepContent.BLEEP.get(), FAR);
        bleep.tame(owner); // taming earns its owner an advancement: sparkles first
        helper.runAfterDelay(110, () -> {
            bleep.showDisplay(io.hookdev.bleep.entity.Display.FACE, 0); // a hi from a bleep next door may be on its screen
            io.hookdev.bleep.Bleep.react(owner, io.hookdev.bleep.entity.Display.YUM);
            helper.assertTrue(bleep.display() == io.hookdev.bleep.entity.Display.YUM, "its owner ate something: a little heart on its screen, it shows " + bleep.display());
            helper.assertFalse(bleep.react(io.hookdev.bleep.entity.Display.SPARKLE), "not twice in a row");
            helper.succeed();
        });
    }

    private static Player withCore(GameTestHelper helper) {
        Player player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BleepContent.BLEEP_CORE.get()));
        return player;
    }

    /** Uses the core in the player's hand on the ground in the middle of the bench. */
    private static void useCore(GameTestHelper helper, Player player) {
        BlockPos floor = helper.absolutePos(SPOT.below());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        BleepContent.BLEEP_CORE.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static void theCoreNeedsItsParts(GameTestHelper helper) {
        Player player = withCore(helper);
        player.getInventory().add(new ItemStack(BleepContent.BLEEP_TRACKS.get()));
        useCore(helper, player);
        helper.assertTrue(player.getMainHandItem().is(BleepContent.BLEEP_CORE.get()), "without a display and arms the core stays in your hand");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(BleepEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(SPOT)).inflate(8)).isEmpty(), "no bleep without all its parts");
        helper.assertTrue(player.getInventory().contains(new ItemStack(BleepContent.BLEEP_TRACKS.get())), "the parts you have stay yours");
        helper.succeed();
    }

    private static void theCorePutsABleepTogether(GameTestHelper helper) {
        Player player = withCore(helper);
        for (var part : BleepCoreItem.parts()) player.getInventory().add(new ItemStack(part));
        useCore(helper, player);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the core should go into the bleep");
        for (var part : BleepCoreItem.parts()) helper.assertFalse(player.getInventory().contains(new ItemStack(part)), "the " + part + " should go into the bleep");
        BleepEntity bleep = helper.findOneEntity(BleepContent.BLEEP.get());
        helper.assertTrue(bleep.isOwnedBy(player), "the bleep should belong to whoever built it");
        helper.assertTrue(bleep.assembly() > 0, "it should be put together part by part");
        helper.runAfterDelay(BleepEntity.ASSEMBLE_TICKS + 5, () -> helper.assertTrue(bleep.emotion() == Emotion.BOOT, "once together its screen starts up, it shows " + bleep.emotion()));
        helper.runAfterDelay(BleepEntity.ASSEMBLE_TICKS + 10, () -> helper.succeedWhen(() -> helper.assertTrue(bleep.emotion() == Emotion.EXCITED, "after starting up it should be excited, it shows " + bleep.emotion())));
    }

    /** Opens a chest of this loot table many times over and counts a part. */
    private static int rolls(GameTestHelper helper, String table, net.minecraft.world.item.Item part, int times) {
        var level = helper.getLevel();
        var loot = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, net.minecraft.resources.Identifier.withDefaultNamespace(table)));
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        int found = 0;
        for (int i = 0; i < times; i++) for (ItemStack stack : loot.getRandomItems(params)) if (stack.is(part)) found++;
        return found;
    }

    private static void partsTurnUpInChests(GameTestHelper helper) {
        helper.assertTrue(rolls(helper, "chests/simple_dungeon", BleepContent.BLEEP_CORE.get(), 100) > 0, "a dungeon chest should sometimes hold a core");
        helper.assertTrue(rolls(helper, "chests/abandoned_mineshaft", BleepContent.BLEEP_CORE.get(), 100) > 0, "a mineshaft chest should sometimes hold a core");
        helper.assertTrue(rolls(helper, "chests/village/village_plains_house", BleepContent.BLEEP_CORE.get(), 100) == 0, "a plain house has no core");
        helper.succeed();
    }
}
