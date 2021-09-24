import com.bob.jr.TextToSpeech.TextToSpeech;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TextToSpeechTest {

    @Test
    public void verifyTextToSpeech() {
        try {
            TextToSpeech textToSpeech = new TextToSpeech();
            textToSpeech.synthesisText("ERROR ERROR ERROR!");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
