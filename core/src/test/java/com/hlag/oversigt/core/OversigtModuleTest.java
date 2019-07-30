package com.hlag.oversigt.core;

import java.util.Optional;

public class OversigtModuleTest {
	public static OversigtModule createOversigtModule() {
		return new OversigtModule(Optional.empty(), () -> {/* do nothing */});
	}
}
