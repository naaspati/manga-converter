package samrock.converters.makestrip;

import java.nio.file.Path;

import sam.collection.OneOrMany;
import samrock.converters.extras.ConvertTask;
import samrock.converters.extras.Errors;

class MakeStripResult {
    final ConvertTask task;
    final Errors errors;
    final OneOrMany<Path> convertedFiles;
    
    public MakeStripResult(ConvertTask task, Errors errors, OneOrMany<Path> convertedFiles) {
        this.task = task;
        this.errors = errors;
        this.convertedFiles = convertedFiles;
    }
}
