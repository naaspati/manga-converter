package samrock.converters.makestrip;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
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

}
