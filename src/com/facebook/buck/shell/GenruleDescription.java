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

package com.facebook.buck.shell;

import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.SourcePathRuleFinder;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;

import java.util.Optional;

public class GenruleDescription extends AbstractGenruleDescription<GenruleDescription.Arg> {

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  protected <A extends GenruleDescription.Arg> BuildRule createBuildRule(
      BuildRuleParams params,
      BuildRuleResolver resolver,
      A args,
      Optional<com.facebook.buck.rules.args.Arg> cmd,
      Optional<com.facebook.buck.rules.args.Arg> bash,
      Optional<com.facebook.buck.rules.args.Arg> cmdExe) {
    SourcePathRuleFinder ruleFinder = new SourcePathRuleFinder(resolver);
    SourcePathResolver pathResolver = new SourcePathResolver(ruleFinder);
    if (!args.executable.orElse(false)) {
      return new Genrule(
          params,
          pathResolver,
          args.srcs,
          cmd,
          bash,
          cmdExe,
          args.type,
          args.out);
    } else {
      return new GenruleBinary(
          params,
          pathResolver,
          args.srcs,
          cmd,
          bash,
          cmdExe,
          args.type,
          args.out);
    }
  }

  @SuppressFieldNotInitialized
  public static class Arg extends AbstractGenruleDescription.Arg {
    public Optional<Boolean> executable;
  }

}
