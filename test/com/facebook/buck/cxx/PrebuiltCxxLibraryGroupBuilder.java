/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.cxx;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.SourcePath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;

import java.util.Optional;
import java.util.regex.Pattern;


public class PrebuiltCxxLibraryGroupBuilder
    extends
    AbstractNodeBuilder<
        PrebuiltCxxLibraryGroupDescription.Args,
        PrebuiltCxxLibraryGroupDescription> {

  public PrebuiltCxxLibraryGroupBuilder(BuildTarget target) {
    super(PrebuiltCxxLibraryGroupDescription.of(), target);
  }

  public PrebuiltCxxLibraryGroupBuilder setExportedPreprocessorFlags(ImmutableList<String> flags) {
    arg.exportedPreprocessorFlags = flags;
    return this;
  }

  public PrebuiltCxxLibraryGroupBuilder setIncludeDirs(ImmutableList<SourcePath> includeDirs) {
    arg.includeDirs = includeDirs;
    return this;
  }

  public PrebuiltCxxLibraryGroupBuilder setStaticLink(ImmutableList<String> args) {
    arg.staticLink = args;
    return this;
  }

  public PrebuiltCxxLibraryGroupBuilder setStaticLibs(ImmutableList<SourcePath> args) {
    arg.staticLibs = args;
    return this;
  }

  public PrebuiltCxxLibraryGroupBuilder setStaticPicLink(ImmutableList<String> args) {
    arg.staticPicLink = args;
    return this;
  }

  public PrebuiltCxxLibraryGroupBuilder setStaticPicLibs(ImmutableList<SourcePath> args) {
    arg.staticPicLibs = args;
    return this;
  }

  public PrebuiltCxxLibraryGroupBuilder setSharedLink(ImmutableList<String> args) {
    arg.sharedLink = args;
    return this;
  }

  public PrebuiltCxxLibraryGroupBuilder setSharedLibs(ImmutableMap<String, SourcePath> args) {
    arg.sharedLibs = args;
    return this;
  }

  public PrebuiltCxxLibraryGroupBuilder setProvidedSharedLibs(
      ImmutableMap<String, SourcePath> args) {
    arg.providedSharedLibs = args;
    return this;
  }

  public PrebuiltCxxLibraryGroupBuilder setDeps(ImmutableSortedSet<BuildTarget> deps) {
    arg.deps = deps;
    return this;
  }

  public PrebuiltCxxLibraryGroupBuilder setExportedDeps(ImmutableSortedSet<BuildTarget> deps) {
    arg.exportedDeps = deps;
    return this;
  }

  public PrebuiltCxxLibraryGroupBuilder setSupportedPlatformsRegex(Pattern pattern) {
    arg.supportedPlatformsRegex = Optional.of(pattern);
    return this;
  }

}
