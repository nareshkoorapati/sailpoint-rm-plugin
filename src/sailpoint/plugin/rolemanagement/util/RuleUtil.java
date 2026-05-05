package sailpoint.plugin.rolemanagement.util;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Rule;
import sailpoint.tools.GeneralException;

public class RuleUtil {
	private static final Log log = LogFactory.getLog(RuleUtil.class);
	
	
	public RuleUtil() {
		super();
	}
	
	public static Object runIIQRule(SailPointContext context, String ruleName, Map<String,Object> params ) throws GeneralException {
		String logPrefix = "runIIQRule :: ";
		log.debug(logPrefix+" rule name "+ruleName);
		Object result = null;
		try {
			if (params == null) {
				params = new HashMap<String,Object>();
			}
			params.put("context", context);
			Rule rule = context.getObjectByName(Rule.class, ruleName);
			if (rule == null) {
				throw new GeneralException(logPrefix+"Rule not found :"+ruleName);
			}
			result = context.runRule(rule, params);
		}
		catch(Exception e) {
			log.error(logPrefix+" erro is "+e.getMessage());
			throw e;
		}
		return result;
		
	}
	
	
}
