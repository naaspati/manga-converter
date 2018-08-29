package samrock.converters.extras;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

public class Formatter extends SimpleFormatter {
    @Override
    public synchronized String format(LogRecord record) {
        if(record.getLevel() == Level.INFO)
            return record.getMessage().concat("\n");
        return super.format(record);
    }

}
