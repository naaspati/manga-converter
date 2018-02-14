package samrock.converters.extras;

import java.nio.file.Path;

public class SourceTarget {
    private final Path source, target;

    public SourceTarget(Path source, Path target) {
        this.source = source;
        this.target= target;
    }
    @Override
    public String toString() {
        return "SourceTarget [source=" + source + ", target=" + target + "]";
    }
    public Path getSource() {
        return source;
    }
    public Path getTarget() {
        return target;
    }
}
