package samrock.converters.filechecker;

import static samrock.converters.extras.Utils.DATA_FOLDER;
import static samrock.converters.extras.Utils.DONT_SKIP_NUMBER_MISSINGS_CHECK;
import static samrock.converters.extras.Utils.MANGA_FOLDER;
import static samrock.converters.extras.Utils.MAX_FILE_NUMBER;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import samrock.converters.extras.Errors;

public class FilesChecker {
    final static int mcount = MANGA_FOLDER.getNameCount() + 1;
    final static int dcount = DATA_FOLDER.getNameCount() + 1;
    
    public static ResultOrErrors check(Path src, Path target) throws IOException {
        if(!Files.isDirectory(src)) 
            return new ResultOrErrors(Errors.generalErrors(Files.notExists(src) ? "Folder not found: " : "not a folder: ", src));

        checkBadDir(src);
        if(target != null && !target.equals(src) && Files.exists(target))
            checkBadDir(target);

        return checkDir(src);
    }
    private static void checkBadDir(Path path) throws IOException {
        if(path.equals(MANGA_FOLDER) || Files.isSameFile(path, MANGA_FOLDER))
            throw new IllegalArgumentException("trying to convert manga_folder: "+MANGA_FOLDER);
        if(path.equals(DATA_FOLDER) || Files.isSameFile(path, DATA_FOLDER))
            throw new IllegalArgumentException("trying to convert data_folder: "+DATA_FOLDER);
        if(path.startsWith(MANGA_FOLDER) && path.getNameCount() == mcount)
            throw new IllegalArgumentException("trying to convert manga_dir in manga_folder: "+path);
        if(path.startsWith(DATA_FOLDER) && path.getNameCount() == dcount)
            throw new IllegalArgumentException("trying to convert data_dir in data_folder: "+path);
    }
    private static ResultOrErrors checkDir(Path source) {
        Errors errors = new Errors(source);

        Path[] paths = new Path[30];
        int max = 0;
        int fileCount = 0;

        try(DirectoryStream<Path> stream = Files.newDirectoryStream(source);) {
            for (Path path : stream) {
                if(Files.isDirectory(path))
                    errors.addDirectoryInside(path.getFileName());
                else {
                    String name = path.getFileName().toString();
                    try {
                        int n = name.indexOf('.');
                        n = Integer.parseInt(n < 0 ? name : name.substring(0, n));
                        if(n > MAX_FILE_NUMBER) {
                            errors.addGeneralError(null, "max file number(",MAX_FILE_NUMBER,") exceeded: ", name);
                            return new ResultOrErrors(errors);
                        }
                        if(n >= paths.length) 
                            paths = Arrays.copyOf(paths, n + 30);

                        if(paths[n] != null)
                            errors.addImageError(null, "number conflits: "+paths[n].getFileName() +" -> "+name);
                        else 
                            paths[n] = path;

                        if(n > max)
                            max = n;
                        fileCount++;
                    } catch (NumberFormatException e) {
                        errors.addFileErrors(e, "bad file number: ", name);
                    }
                }
            }            
        } catch (IOException e1) {
            errors.addGeneralError(e1, "dir walk failed: ",source);
            return new ResultOrErrors(errors);
        }
        if(fileCount == 0) {
            errors.addGeneralError(null, "Empty dir");
            return new ResultOrErrors(errors);
        }

        Path[] paths2 = paths;
        int[] missings = !DONT_SKIP_NUMBER_MISSINGS_CHECK ? null : IntStream.range(0, max + 1).filter(i -> paths2[i] == null).toArray();

        if(missings != null && missings.length != 0)
            errors.addMissingsImages(missings);

        if(errors.hasError()) new ResultOrErrors(errors);

        return new ResultOrErrors(Stream.of(paths).limit(max+1).map(f -> f == null ? null : new CheckedFile(f)).toArray(CheckedFile[]::new));
    }
}
