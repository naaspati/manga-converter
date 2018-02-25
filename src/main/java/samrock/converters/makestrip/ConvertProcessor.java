package samrock.converters.makestrip;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import sam.console.ansi.ANSI;
import sam.manga.newsamrock.chapters.ChapterWithMangaId;
import sam.manga.newsamrock.converter.ConvertChapter;
import sam.properties.myconfig.MyConfig;
import sam.tsv.Tsv;
import samrock.converters.cleanupupdate.CheckupsAndUpdates;
import samrock.converters.extras.Progressor;

public class ConvertProcessor {
    public static void process(Path chaptersDataFile, boolean onlyMove) throws IOException {
        Objects.requireNonNull(chaptersDataFile);

        Tsv tsv = Tsv.parse(chaptersDataFile);
        if(tsv.isEmpty()) {
            System.out.println(ANSI.red("file does not have data: ")+chaptersDataFile);
            return;
        }

        List<ConvertChapter> chapters = ConvertChapter.parse(tsv);
        process(chapters, onlyMove);
    }
    public static void process(List<ConvertChapter> chapters, boolean onlyMove) throws IOException {
        Progressor progress = chapters.size() < 4 ? new Progressor() : new Progressor("", chapters.size());

        Converter converter = new Converter(progress);
        if(onlyMove) {
            // converter.move(); 
            throw new IllegalAccessError("converter.move() not yet implemented");
        }
        else {
            List<ChapterWithMangaId> chs = converter.convert(Collections.unmodifiableList(chapters));
            Path mp = Paths.get(MyConfig.MANGA_FOLDER); 
            if(chs != null && chapters.stream().anyMatch(c -> c.getTarget() != null && c.getTarget().startsWith(mp))) {
                CheckupsAndUpdates c = new CheckupsAndUpdates(chs, progress);
                c.start();
            }
            progress.dispose();
        }
    }

    public static List<File> checkfoldersNotConverted(Map<String, Long> modifiedDates){
        List<File> conversion = new ArrayList<>();

        Progressor progressor = new Progressor("Checking folders not converted", 100); 
        progressor.setString("Listing manga in: "+MyConfig.MANGA_FOLDER);

        File root = new File(MyConfig.MANGA_FOLDER);
        String[] mangaNames = root.list();

        progressor.setMaximum(mangaNames.length);
        progressor.resetProgress();
        progressor.setString("Checking");

        int skipped = 0;
        for (String name : mangaNames) {
            if(name.equals("desktop.ini") || name.equals("Data"))
                continue;
            File file = new File(root, name);
            Long lm = file.lastModified();

            if(Objects.equals(lm, modifiedDates.get(name))){
                skipped++;
                continue;
            }
            modifiedDates.put(name, lm);

            File[] files = null;
            if(file.isDirectory() && (files = file.listFiles(File::isDirectory)).length != 0){
                System.out.println(ANSI.yellow(name));
                for (File f : files) {
                    conversion.add(f);
                    System.out.println("  "+f.getName());
                }
                System.out.println();
            }
            progressor.increaseBy1();
        }
        
        progressor.setCompleted();

        System.out.println(ANSI.yellow("SKIPPED: ")+skipped);
        
        progressor.dispose();
        return conversion;
    }

}
