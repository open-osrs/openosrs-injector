package com.openosrs.injector;

import com.openosrs.injector.injection.InjectData;
import com.openosrs.injector.rsapi.RSApi;
import static com.openosrs.injector.rsapi.RSApi.API_BASE;
import com.openosrs.injector.rsapi.RSApiClass;
import com.openosrs.injector.rsapi.RSApiMethod;
import net.runelite.asm.ClassFile;
import net.runelite.asm.pool.Class;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class InjectorValidator implements Validator
{
	private static final Logger log = Logging.getLogger(InjectorValidator.class);
	private static final String OK = "OK", ERROR = "ERROR", WTF = "WTF";
	private final InjectData inject;

	private int missing = 0, okay = 0, wtf = 0;

	InjectorValidator(InjectData inject)
	{
		this.inject = inject;
	}

	public boolean validate()
	{
		final RSApi rsApi = inject.getRsApi();
		for (ClassFile cf : inject.getVanilla())
		{
			for (Class intf : cf.getInterfaces())
			{
				if (intf.getName().startsWith(API_BASE))
				{
					continue;
				}

				RSApiClass apiC = rsApi.findClass(intf.getName());
				if (apiC == null)
				{
					log.error("{} is rs api type implemented by {} but it doesn't exist in rsapi. wtf", intf, cf.getPoolClass());
					++wtf;
					continue;
				}

				check(cf, apiC);
			}
		}

		String status = wtf > 0 ? WTF : missing > 0 ? ERROR : OK;
		log.info("RSApiValidator completed. Status [{}] {} overridden methods, {} missing", status, okay, missing);

		// valid, ref to static final field
		return status == OK;
	}

	private void check(ClassFile clazz, RSApiClass apiClass)
	{
		for (RSApiMethod apiMethod : apiClass)
		{
			if (apiMethod.isSynthetic() || apiMethod.isDefault())
			{
				continue;
			}

			if (clazz.findMethodDeep(apiMethod.getName(), apiMethod.getSignature()) == null)
			{
				log.warn("Class {} implements interface {} but doesn't implement {}",
					clazz.getPoolClass(), apiClass.getClazz(), apiMethod.getMethod());
				++missing;
			}
			else
			{
				++okay;
			}
		}
	}
}
