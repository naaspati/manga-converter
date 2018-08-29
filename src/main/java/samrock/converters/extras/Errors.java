package samrock.converters.extras;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import samrock.converters.extras.Utils;

public class Errors extends HashMap<String, List<Object>> {
    private static final long serialVersionUID = -7398299680125297994L;

    private final Path fullPath;

    public Errors(Path f) {
        this.fullPath = f;
    }
    public Path getSubpath() {
        return Utils.subpath(fullPath);
    }
    public Path getFullPath() {
        return fullPath;
    }
    public void addDirectoryInside(Path fileName) {
        addError("Directory found", null, String.valueOf(fileName));
    }
    public void addImageError(Throwable e, Object...msg) {
        addError("Image Error", e, msg);
    }
    public void addGeneralError(Throwable e, Object...msg) {
        addError("General", e, msg);
    }
    public void addFileErrors(NumberFormatException e, Object...msg) {
        addError("File Error", e, msg);
    }
    public void addImageSizeError(Path subpath, int w, int h) {
        addError("Image size Error", null, subpath, "(",w,"X",h,")");   
    }
    public void setCancelled() {
        List<Object> list = getBuilder("CANCELLED");
        list.add("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
        list.add("xxxxxxxxxxx CANCELLED xxxxxxxxxxx");
        list.add("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
    }
    public void addDoublePagesError(List<Path> doublePages) {
        List<Object> list = getBuilder("Possibly double paged");
        list.addAll(doublePages);
    }
    public void addMoveFailed(IOException e, Object...msg) {
        addError("Moving fails", e, msg);
    }
    public void addMissingsImages(int[] missings) {
        if(missings == null || missings.length == 0)
            return;

        getBuilder("Missing Image Files").add(Arrays.toString(missings));
        
    }
    public void addGarbagedError(Path source, Path target) {
        getBuilder("Garbaged").add(source+" -> "+target);
    }
    private void addError(String title, Throwable e, Object...msg) {
        if(e == null && msg == null)
            return;

        StringBuilder list = new StringBuilder();

        if(msg != null) {
            for (Object o : msg)
                list.append(o).append(' ');
            list.append(" --> ");
        }
        if(e != null) {
            list.append("  [")
            .append(e.getClass().getSimpleName())
            .append(": ");
            if(e.getMessage() != null)
                list.append(e.getMessage());
            list.append(']');
        }
        
        getBuilder(title).add(list);
    }
    private List<Object> getBuilder(String title) {
        List<Object> list = get(title);
        if(list == null)
            put(title, list = new ArrayList<>());
        return list;
    }
    public boolean hasError() {
        return !isEmpty();
    }
    public StringBuilder appendTo(StringBuilder sb) {
        this.forEach((key, list) -> {
            sb.append('\n').append(key).append('\n');
            list.forEach(s -> sb.append("  ").append(s).append('\n'));
        });
        return sb;
    }
    @Override
    public String toString() {
        if(isEmpty()) return null;
        return appendTo(new StringBuilder().append(fullPath)).toString();
    }
    public static Errors generalErrors(String msg, Path src) {
        Errors e = new Errors(src);
        e.addGeneralError(null, msg);
        return e;
    }
}
