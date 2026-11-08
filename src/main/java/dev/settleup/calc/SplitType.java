package dev.settleup.calc;

/** How an expense is divided among the people it was for. */
public enum SplitType {
    /** Divided evenly. Values are ignored; an empty allocation list means "everyone in the group". */
    EQUAL,
    /** Each value is an exact amount; values must sum to the expense amount. */
    EXACT,
    /** Each value is a percentage; values must sum to exactly 100. */
    PERCENTAGE,
    /** Each value is a relative weight (e.g. nights stayed: 2, 3, 3). */
    SHARES
}
