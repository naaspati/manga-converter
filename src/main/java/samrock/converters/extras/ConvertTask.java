package samrock.converters.extras;

import java.nio.file.Path;

import samrock.converters.extras.SourceTarget;
import samrock.converters.filechecker.CheckedFile;

public class ConvertTask {
    private final Path source;
    private final Path target;
    private final CheckedFile[] files;
    
    public ConvertTask(Path source, Path target, CheckedFile[] files) {
        this.source = source;
        this.target = target;
        this.files = files;
    }
    public ConvertTask(SourceTarget st, CheckedFile[] files) {
        this.source = st.getSource();
        this.target = st.getTarget();
        this.files = files;
    }
    public Path getSource() {
        return source;
    }
    public Path getTarget() {
        return target;
    }
    public CheckedFile[] getFiles() {
        return files;
    }
}
