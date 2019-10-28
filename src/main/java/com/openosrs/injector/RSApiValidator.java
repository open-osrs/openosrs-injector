package com.openosrs.injector;

import com.openosrs.injector.injection.InjectData;
import com.openosrs.injector.rsapi.RSApi;
import com.openosrs.injector.rsapi.RSApiClass;
import com.openosrs.injector.rsapi.RSApiMethod;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.impldep.aQute.libg.cryptography.RSA;

public class RSApiValidator
{
	private static final Logger log = Logging.getLogger(RSApiValidator.class);
	private final InjectData inject;

	public RSApiValidator(InjectData inject)
	{
		this.inject = inject;
	}

	public boolean validate() throws Injexception
	{
		RSApi rsApi = inject.getRsApi();
		for (RSApiClass apiClass : rsApi)
		{
			for (RSApiMethod apiMethod : apiClass)
			{
				if (!apiMethod.isInjected())
					if (!apiMethod.isSynthetic())
						if (inject.toVanilla(inject.toDeob(apiClass.getName())).findMethod(apiMethod.getName(), apiMethod.getSignature()) == null)
							log.warn("{} not injected", apiMethod.getMethod());
			}
		}

		return false;
	}
}
