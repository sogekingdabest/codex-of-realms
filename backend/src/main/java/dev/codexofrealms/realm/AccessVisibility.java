package dev.codexofrealms.realm;

/**
 * Who may read a record, as other modules and the API expose it: every member, only owners and
 * editors, or the players a spoiler has been revealed to.
 */
public enum AccessVisibility {
    PUBLIC,
    GM_ONLY,
    SPOILER
}
