package samrock.converters.filechecker;

import java.nio.file.Path;

public class CheckedFile {
    private final Path path;

    CheckedFile(Path path) {
        this.path = path;
    }
    public Path getPath() {
        return path;
    }
}