/*
 * Copyright 2016-present Facebook, Inc.
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

package com.facebook.buck.d;

import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.CxxPlatformUtils;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;

import java.util.Optional;

public class DTestBuilder extends AbstractNodeBuilder<DTestDescription.Arg, DTestDescription> {

  public DTestBuilder(
      BuildTarget target,
      DBuckConfig dBuckConfig,
      CxxPlatform defaultCxxPlatform) {
    super(
        new DTestDescription(
            dBuckConfig,
            CxxPlatformUtils.DEFAULT_CONFIG,
            defaultCxxPlatform,
            Optional.empty()),
        target);
  }

}
