package samrock.converters.app.main;

import java.io.File;
import java.util.List;

import com.beust.jcommander.Parameter;

class Split extends ConvertOption {
    @Parameter
    List<String> args;
    
    @Parameter(names= "--batch" , description = "Batch Double Page Splitter")
    public File batchDoublePageSplitter;
}