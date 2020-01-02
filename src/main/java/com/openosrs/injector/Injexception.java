/*
 * Copyright (c) 2019, Lucas <https://github.com/Lucwousin>
 * All rights reserved.
 *
 * This code is licensed under GPL3, see the complete license in
 * the LICENSE file in the root directory of this source tree.
 */
package com.openosrs.injector;

public class Injexception extends RuntimeException
{
	public Injexception(String message)
	{
		super(message);
	}

	public Injexception(Throwable cause)
	{
		super(cause);
	}

	public Injexception(String message, Throwable cause)
	{
		super(message, cause);
	}
}
