package samrock.converters.app.main;
import java.util.List;

import com.beust.jcommander.Parameter;

class Convert extends ConvertOption {
    public Convert() {
        tempChapter = System.getenv("CHAPTERS_DATA_FILE");
    }
    
    public String tempChapter; 
    @Parameter
    public List<String> chapters;
    
    public void setChapterFile() {
        if(chapters != null)
            System.setProperty("CHAPTERS_DATA_FILE", String.join(";", chapters));
        else if(tempChapter != null)
            System.setProperty("CHAPTERS_DATA_FILE", tempChapter);
    }
    @Parameter(names= {"--move-only"}, description =  "only Moves Mangarock Data", order=1)
    public boolean mangarockMoveOnly;
    @Parameter(names= {"--resume" }, description =  "Resume Previously Cancelled Mangarock Conversion", order=2)
    public boolean resumeMangarockConversion;
    @Parameter(names= {"--dirs"}, description =  "Convert Folders In Folder", order=3)
    public String convertFoldersInFolder;
}