package samrock.converters.app.main;

import static sam.console.ANSI.red;
import static sam.myutils.System2.lookup;

import java.util.logging.Logger;

import sam.logging.MyLoggerFactory;
import sam.myutils.System2;
public abstract class CmdInitBase implements CmdInit {
	private static final Logger LOGGER = MyLoggerFactory.logger(CmdInitBase.class);
	
	protected  void novalueMsg(String cmd, String valueShouldBe) {
		LOGGER.info(red("no value specified for: "+cmd)+", should be: " +valueShouldBe);
	}
	protected  static int lookupInt(String key, int minimum) {
		int n = Integer.parseInt(lookup(key));
		if(n < minimum)
			throw new IllegalStateException("Illegal value for key: "+key+", minimum: "+minimum);
    	return n;
    }
	protected  static boolean lookupBoolean(String key, boolean defaultValue) {
    	return System2.lookupBoolean(key, defaultValue);
    }

}
