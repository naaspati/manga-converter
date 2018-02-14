package samrock.converters.makestrip;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import sam.console.ansi.ANSI;
import sam.manga.newsamrock.converter.ConvertChapter;
import sam.tsv.Tsv;
import samrock.converters.cleanupupdate.CheckupsAndUpdates;
import samrock.converters.extras.Progressor;
import samrock.converters.extras.SourceTarget;

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
        
        Converter converter = new Converter(chapters.stream().map(c -> new SourceTarget(c.getSource(), c.getTarget())).collect(Collectors.toList()), progress);
        if(onlyMove)
            converter.move();
        else {
            converter.convert();
            CheckupsAndUpdates c = new CheckupsAndUpdates(chapters, progress);
            c.start();
        }
    }

}
