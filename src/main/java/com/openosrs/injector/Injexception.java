package com.openosrs.injector;

public class Injexception extends Exception
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
