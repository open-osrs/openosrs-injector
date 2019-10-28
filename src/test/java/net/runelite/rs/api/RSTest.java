package net.runelite.rs.api;

import javax.inject.Inject;
import net.runelite.mapping.Import;

public interface RSTest extends RSInterface
{
	@Import("test1")
	void setTest1(String test1);

	@Import("test1")
	String getTest1();

	@Import("test2")
	@Inject
	void invokeTest2(String var1, int var3, String var2);

	@Import("test3")
	void setTest3(String test1);
}
