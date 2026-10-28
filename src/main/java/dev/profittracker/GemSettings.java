package dev.profittracker;

public final class GemSettings {
    public boolean hudEnabled = true;
    public boolean miningTimeOnly = false;
    public int timerModeVersion = 1;
    public boolean includeGlossy = false;
    public int averageMinutes = 3;
    public int glossyFormatVersion = 1;
    public String glossyFormat = GemMessages.DEFAULT_GLOSSY_FORMAT;

    public void sanitize() {
        averageMinutes = Math.clamp(averageMinutes, 1, 10);
        glossyFormat = ChatTitleRule.clean(glossyFormat);
        if (!GemMessages.validGlossyFormat(glossyFormat)) glossyFormat = GemMessages.DEFAULT_GLOSSY_FORMAT;
    }
}
