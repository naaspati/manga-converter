package samrock.converters.extras;

import java.nio.file.Path;

import sam.collection.OneOrMany;
import samrock.converters.filechecker.CheckedFile;

public class ConvertTask {
    private final Object userObject;
    
    private final CheckedFile[] files;
    private final Path source;
    private final Path target;
    
    private OneOrMany<Path> result;
    
    public ConvertTask(Path source, Path target, CheckedFile[] files, Object userObject) {
        this.userObject = userObject;
        this.files = files;
        this.source = source;
        this.target = target;
    }
    public ConvertTask(Path source, CheckedFile[] files) {
        this.source = source;
        this.target = null;
        this.files = files;
        this.userObject = null;
    }
    public Object getUserObject() {
        return userObject;
    }
    public CheckedFile[] getFiles() {
        return files;
    }
    public Path getTarget() {
        return target;
    }
    public Path getSource() {
        return source;
    }
    public OneOrMany<Path> getResult() {
        return result;
    }
    public void setResult(OneOrMany<Path> result) {
        this.result = result;
    }
}
