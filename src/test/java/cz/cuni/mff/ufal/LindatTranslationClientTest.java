package cz.cuni.mff.ufal;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class LindatTranslationClientTest {

    static Translator translator;

    @BeforeClass
    public static void beforeInit(){
        translator = new LindatTranslationClient("https://lindat.mff.cuni.cz/services/translation/api/v2");
        //translator = new LindatTranslationClient();
    }

    @Test
    public void translateWithSrcTgt() {
        assertTrue(translator.translate("This is a simple test.", "en", "cs")
                .startsWith("Tohle je jednoduchý test."));
    }

    @Test(expected = IllegalArgumentException.class)
    public void translateParamSanity(){
        translator.translate("Hope we never have English-Dumpu model", "en", "wtf");
    }

    @Test
    public void getAvailableLanguagePairs() {
        assertFalse(translator.getAvailableLanguagePairs().isEmpty());
    }

    @Test
    public void getAvailableModels() {
        assertFalse(translator.getAvailableModels().isEmpty());
    }

    @Test
    public void translateWithModel() {
        assertTrue(translator.translate("This is a simple test.", "en-cs")
                .startsWith("Tohle je jednoduchý test."));
    }
}
