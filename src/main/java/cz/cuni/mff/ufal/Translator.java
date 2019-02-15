package cz.cuni.mff.ufal;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Translator{
    public String translate(String text, String sourceLanguage, String targetLanguage);
    public String translate(String text, String modelName);
    public Set<Map.Entry<String, String>> getAvailableLanguagePairs();
    public Set<String> getAvailableModels();
}