import com.bob.jr.TextToSpeech;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Text;

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
