/*
 * JBoss, Home of Professional Open Source. Copyright 2014 Red Hat, Inc., and
 * individual contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.hlag.oversigt.util;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

import com.google.common.io.Resources;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.undertow.UndertowMessages;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceChangeListener;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.server.handlers.resource.URLResource;

/**
 * @author Stuart Douglas
 * @author Olaf Neumann
 */
public class ClassPathResourceManager implements ResourceManager {
	/**
	 * The prefix that is appended to resources that are to be loaded.
	 */
	private final String prefix;

	public ClassPathResourceManager(final Package p) {
		this(p.getName().replace(".", "/"));
	}

	public ClassPathResourceManager(final String prefix) {
		if (prefix.isEmpty()) {
			this.prefix = "";
		} else if (prefix.endsWith("/")) {
			this.prefix = prefix;
		} else {
			this.prefix = prefix + "/";
		}
	}

	@Override
	public @Nullable Resource getResource(@Nullable final String path) throws IOException {
		String modPath = Objects.requireNonNull(path);
		if (modPath.startsWith("/")) {
			modPath = path.substring(1);
		}
		final String realPath = prefix + modPath;
		try {
			return new URLResource(getResourceUrl(realPath), path);
		} catch (@SuppressWarnings("unused") final IllegalArgumentException ignore) {
			return null;
		}
	}

	/**
	 * Find the URL for a resource
	 *
	 * @param realPath the path to the resource for which to find the URL
	 * @return the URL to the denoted resource
	 * @throws IllegalArgumentException if the the resource cannot be found
	 */
	protected URL getResourceUrl(final String realPath) throws IllegalArgumentException {
		return Resources.getResource(realPath);
	}

	@Override
	public boolean isResourceChangeListenerSupported() {
		return false;
	}

	@Override
	public void registerResourceChangeListener(
			@SuppressWarnings("unused") @Nullable final ResourceChangeListener listener) {
		throw UndertowMessages.MESSAGES.resourceChangeListenerNotSupported();
	}

	@Override
	public void removeResourceChangeListener(
			@SuppressWarnings("unused") @Nullable final ResourceChangeListener listener) {
		throw UndertowMessages.MESSAGES.resourceChangeListenerNotSupported();
	}

	@Override
	public void close() throws IOException {/* nothing to close here */}
}
