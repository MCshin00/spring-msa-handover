package io.hotdealwave.server.analysis;

/** The analysed repository and its fixed commit, from the extraction's target file. */
public record Target(String name, String repository, String commit) {
    public String shortCommit() { return commit.substring(0, 7); }
    /** Where an evidence path of the target opens at the fixed commit. */
    public String source() { return repository + "/blob/" + commit + "/"; }
}
