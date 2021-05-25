import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Random;

public class BobJrTest {

    @Test
    public void loadAllFilesInDir() throws IOException {
         File directory = new File(this.getClass().getClassLoader().getResource("fart").getFile());
         assertTrue(directory.isDirectory());
         File[] directoryContents = directory.listFiles();
         Random random = new Random();
         int randomInt = random.nextInt(directoryContents.length);
         System.out.println(directoryContents[randomInt].getAbsoluteFile().getAbsolutePath());
    }

}
