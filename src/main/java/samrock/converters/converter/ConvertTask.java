package samrock.converters.converter;

import java.nio.file.Path;

import samrock.converters.cleanupupdate.MangaDirs.Dir;
import samrock.converters.filechecker.CheckedFile;

public class ConvertTask {
    public final Dir dir;
    
    private final CheckedFile[] files;
    public final Path source;
    
    public ConvertTask(Path source, CheckedFile[] files, Dir dir) {
        this.dir = dir;
        this.files = files;
        this.source = source;
    }
    public Dir getDir() {
		return dir;
	}
    public CheckedFile[] getFiles() {
        return files;
    }
    public Path getSource() {
        return source;
    }
}
