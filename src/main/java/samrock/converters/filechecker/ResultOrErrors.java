package samrock.converters.filechecker;

import samrock.converters.extras.Errors;

public class ResultOrErrors {
    private final Errors errors;
    private final CheckedFile[] files;
    
    ResultOrErrors(Errors errors) {
        this.errors = errors;
        this.files = null;
    }
    public ResultOrErrors(CheckedFile[] files) {
        this.errors = null;
        this.files = files;
    }
    
    public boolean hasErrors() {
        return errors != null;
    }
    public Errors getErrors() {
        return errors;
    }
    public CheckedFile[] getFiles() {
        return files;
    }
}