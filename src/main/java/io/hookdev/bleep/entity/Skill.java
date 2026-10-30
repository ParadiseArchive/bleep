package io.hookdev.bleep.entity;

/**
 * What a bleep learns as the bond grows (design doc, Vaardigheden). Once learned it keeps it, even if the bond drops.
 * The level is a bond level (Bond.level): 1 Friend, 2 Buddy, 3 Best friend.
 */
public enum Skill {
    /** Carries a torch in its left hand that lights the way where it drives. */
    TORCH(1),
    /** Fights the monsters that go for its owner, or that its owner goes for. */
    FIGHT(2),
    /** Picks up what lies about (loot, drops) and brings it to its owner. */
    FETCH(2);

    public final int level;

    Skill(int level) {
        this.level = level;
    }

    /** Its message when it is learned: message.bleep.learned.<name>. */
    public String key() {
        return "message.bleep.learned." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
