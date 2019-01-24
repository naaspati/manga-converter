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
    
    @Override
    public String toString() {
    	return String.valueOf(path);
    }
}