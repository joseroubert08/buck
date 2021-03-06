/*
 * Copyright 2015-present Facebook, Inc.
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

package com.facebook.buck.rules;

import static com.facebook.buck.rules.RuleKeyScopedHasher.Scope;
import static com.facebook.buck.rules.RuleKeyHasher.Container;
import static com.facebook.buck.rules.RuleKeyHasher.Wrapper;
import com.facebook.buck.hashing.FileHashLoader;
import com.facebook.buck.io.ArchiveMemberPath;
import com.facebook.buck.log.Logger;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.Either;
import com.facebook.buck.rules.RuleKeyScopedHasher.ContainerScope;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.sha1.Sha1HashCode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

public abstract class RuleKeyBuilder<RULE_KEY> implements RuleKeyObjectSink {

  private static final Logger logger = Logger.get(RuleKeyBuilder.class);

  private final SourcePathRuleFinder ruleFinder;
  private final SourcePathResolver resolver;
  private final FileHashLoader hashLoader;
  private final CountingRuleKeyHasher<HashCode> hasher;
  private final RuleKeyScopedHasher<HashCode> scopedHasher;

  @VisibleForTesting
  protected RuleKeyBuilder(
      SourcePathRuleFinder ruleFinder,
      SourcePathResolver resolver,
      FileHashLoader hashLoader,
      RuleKeyHasher<HashCode> hasher) {
    this.ruleFinder = ruleFinder;
    this.resolver = resolver;
    this.hashLoader = hashLoader;
    this.hasher = new CountingRuleKeyHasher<>(hasher);
    this.scopedHasher = new RuleKeyScopedHasher<>(this.hasher);
  }

  // TODO(plamenko): make this package private once RuleKeyBuilder is moved to the keys subpackage.
  @VisibleForTesting
  public RuleKeyScopedHasher<HashCode> getScopedHasher() {
    return this.scopedHasher;
  }

  public RuleKeyBuilder(
      SourcePathRuleFinder ruleFinder,
      SourcePathResolver resolver,
      FileHashLoader hashLoader) {
    this(
        ruleFinder,
        resolver,
        hashLoader,
        LoggingRuleKeyHasher.of(new GuavaRuleKeyHasher(Hashing.sha1().newHasher())));
  }

  protected RuleKeyBuilder<RULE_KEY> setSourcePath(SourcePath sourcePath) {
    if (sourcePath instanceof ArchiveMemberSourcePath) {
      try {
        return setArchiveMemberPath(
            resolver.getAbsoluteArchiveMemberPath(sourcePath),
            resolver.getRelativeArchiveMemberPath(sourcePath));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    if (sourcePath instanceof BuildTargetSourcePath) {
      BuildTargetSourcePath buildTargetSourcePath = (BuildTargetSourcePath) sourcePath;
      BuildRule buildRule = ruleFinder.getRuleOrThrow(buildTargetSourcePath);
      hasher.putBuildTargetSourcePath(buildTargetSourcePath);
      return setBuildRule(buildRule);
    }

    // The original version of this expected the path to be relative, however, sometimes the
    // deprecated method returned an absolute path, which is obviously less than ideal. If we can,
    // grab the relative path to the output. We also need to hash the contents of the absolute
    // path no matter what.
    Path absolutePath = resolver.getAbsolutePath(sourcePath);
    Path ideallyRelative;
    try {
      ideallyRelative = resolver.getRelativePath(sourcePath);
    } catch (IllegalStateException e) {
      // Expected relative path was absolute. Yay.
      ideallyRelative = absolutePath;
    }
    try {
      return setPath(absolutePath, ideallyRelative);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected RuleKeyBuilder<RULE_KEY> setNonHashingSourcePath(SourcePath sourcePath) {
    String pathForKey;
    if (sourcePath instanceof ResourceSourcePath) {
      pathForKey = ((ResourceSourcePath) sourcePath).getResourceIdentifier();
    } else {
      pathForKey = resolver.getRelativePath(sourcePath).toString();
    }
    hasher.putNonHashingPath(pathForKey);
    return this;
  }

  /**
   * Implementations should ask their factories to compute the rule key for the {@link BuildRule}
   * and call {@link #setBuildRuleKey(RuleKey)} on it.
   */
  protected abstract RuleKeyBuilder<RULE_KEY> setBuildRule(BuildRule rule);

  protected final RuleKeyBuilder<RULE_KEY> setBuildRuleKey(RuleKey ruleKey) {
    try (Scope wraperScope = scopedHasher.wrapperScope(Wrapper.BUILD_RULE)) {
      return setSingleValue(ruleKey);
    }
  }

  /**
   * Implementations should ask their factories to compute the rule key for the
   * {@link RuleKeyAppendable} and call {@link #setAppendableRuleKey(RuleKey)} on it.
   */
  protected abstract RuleKeyBuilder<RULE_KEY> setAppendableRuleKey(RuleKeyAppendable appendable);

  protected final RuleKeyBuilder<RULE_KEY> setAppendableRuleKey(RuleKey ruleKey) {
    try (Scope wraperScope = scopedHasher.wrapperScope(Wrapper.APPENDABLE)){
      return setSingleValue(ruleKey);
    }
  }

  @Override
  public final RuleKeyBuilder<RULE_KEY> setReflectively(String key, @Nullable Object val) {
    try (Scope keyScope = scopedHasher.keyScope(key)) {
      return setReflectively(val);
    }
  }

  @Override
  public final RuleKeyObjectSink setAppendableRuleKey(String key, RuleKeyAppendable appendable) {
    try (Scope keyScope = scopedHasher.keyScope(key)) {
      return setAppendableRuleKey(appendable);
    }
  }

  protected RuleKeyBuilder<RULE_KEY> setReflectively(@Nullable Object val) {
    if (val instanceof RuleKeyAppendable) {
      setAppendableRuleKey((RuleKeyAppendable) val);
      if (!(val instanceof BuildRule)) {
        return this;
      }
      // Explicitly fall through for BuildRule objects so we include
      // their cache keys (which may include more data than appendToRuleKey() does).
    }
    if (val instanceof BuildRule) {
      return setBuildRule((BuildRule) val);
    }

    if (val instanceof Supplier) {
      try (Scope containerScope = scopedHasher.wrapperScope(Wrapper.SUPPLIER)) {
        Object newVal = ((Supplier<?>) val).get();
        return setReflectively(newVal);
      }
    }

    if (val instanceof Optional) {
      Object o = ((Optional<?>) val).orElse(null);
      try (Scope wraperScope = scopedHasher.wrapperScope(Wrapper.OPTIONAL)){
        return setReflectively(o);
      }
    }

    if (val instanceof Either) {
      Either<?, ?> either = (Either<?, ?>) val;
      if (either.isLeft()) {
        try (Scope wraperScope = scopedHasher.wrapperScope(Wrapper.EITHER_LEFT)){
          return setReflectively(either.getLeft());
        }
      } else {
        try (Scope wraperScope = scopedHasher.wrapperScope(Wrapper.EITHER_RIGHT)) {
          return setReflectively(either.getRight());
        }
      }
    }

    // Check to see if we're dealing with a collection of some description.
    // Note {@link java.nio.file.Path} implements "Iterable", so we explicitly exclude it here.
    if (val instanceof Iterable && !(val instanceof Path)) {
      try (ContainerScope containerScope = scopedHasher.containerScope(Container.LIST)) {
        for (Object element : (Iterable<?>) val) {
          try (Scope elementScope = containerScope.elementScope()) {
            setReflectively(element);
          }
        }
        return this;
      }
    }

    if (val instanceof Iterator) {
      Iterator<?> iterator = (Iterator<?>) val;
      try (ContainerScope containerScope = scopedHasher.containerScope(Container.LIST)) {
        while (iterator.hasNext()) {
          try (Scope elementScope = containerScope.elementScope()) {
            setReflectively(iterator.next());
          }
        }
      }
      return this;
    }

    if (val instanceof Map) {
      if (!(val instanceof SortedMap || val instanceof ImmutableMap)) {
        logger.warn(
            "Adding an unsorted map to the rule key. " +
                "Expect unstable ordering and caches misses: %s",
            val);
      }
      try (ContainerScope containerScope = scopedHasher.containerScope(Container.MAP)) {
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) val).entrySet()) {
          try (Scope elementScope = containerScope.elementScope()) {
            setReflectively(entry.getKey());
          }
          try (Scope elementScope = containerScope.elementScope()) {
            setReflectively(entry.getValue());
          }
        }
      }
      return this;
    }

    if (val instanceof Path) {
      throw new HumanReadableException(
          "It's not possible to reliably disambiguate Paths. They are disallowed from rule keys");
    }

    if (val instanceof SourcePath) {
      return setSourcePath((SourcePath) val);
    }

    if (val instanceof NonHashableSourcePathContainer) {
      SourcePath sourcePath = ((NonHashableSourcePathContainer) val).getSourcePath();
      return setNonHashingSourcePath(sourcePath);
    }

    if (val instanceof SourceWithFlags) {
      SourceWithFlags source = (SourceWithFlags) val;
      setSourcePath(source.getSourcePath());
      setReflectively(source.getFlags());
      return this;
    }

    return setSingleValue(val);
  }

  // Paths get added as a combination of the file name and file hash. If the path is absolute
  // then we only include the file name (assuming that it represents a tool of some kind
  // that's being used for compilation or some such). This does mean that if a user renames a
  // file without changing the contents, we have a cache miss. We're going to assume that this
  // doesn't happen that often in practice.
  @Override
  public RuleKeyBuilder<RULE_KEY> setPath(
      Path absolutePath,
      Path ideallyRelative) throws IOException {
    // TODO(shs96c): Enable this precondition once setPath(Path) has been removed.
    // Preconditions.checkState(absolutePath.isAbsolute());
    HashCode sha1 = hashLoader.get(absolutePath);
    if (sha1 == null) {
      throw new RuntimeException("No SHA for " + absolutePath);
    }

    Path addToKey;
    if (ideallyRelative.isAbsolute()) {
      logger.warn(
          "Attempting to add absolute path to rule key. Only using file name: %s", ideallyRelative);
      addToKey = ideallyRelative.getFileName();
    } else {
      addToKey = ideallyRelative;
    }

    hasher.putPath(addToKey, sha1.toString());
    return this;
  }

  public RuleKeyBuilder<RULE_KEY> setArchiveMemberPath(
      ArchiveMemberPath absoluteArchiveMemberPath,
      ArchiveMemberPath relativeArchiveMemberPath) throws IOException {
    Preconditions.checkState(absoluteArchiveMemberPath.isAbsolute());
    Preconditions.checkState(!relativeArchiveMemberPath.isAbsolute());

    HashCode hash = hashLoader.get(absoluteArchiveMemberPath);
    if (hash == null) {
      throw new RuntimeException("No hash for " + absoluteArchiveMemberPath);
    }

    ArchiveMemberPath addToKey = relativeArchiveMemberPath;
    hasher.putArchiveMemberPath(addToKey, hash.toString());
    return this;
  }

  private RuleKeyBuilder<RULE_KEY> setSingleValue(@Nullable Object val) {
    if (val == null) { // Null value first
      hasher.putNull();
    } else if (val instanceof Boolean) {           // JRE types
      hasher.putBoolean((boolean) val);
    } else if (val instanceof Enum) {
      hasher.putString(String.valueOf(val));
    } else if (val instanceof Number) {
      hasher.putNumber((Number) val);
    } else if (val instanceof String) {
      hasher.putString((String) val);
    } else if (val instanceof Pattern) {
      hasher.putPattern((Pattern) val);
    } else if (val instanceof BuildRuleType) {           // Buck types
      hasher.putBuildRuleType((BuildRuleType) val);
    } else if (val instanceof RuleKey) {
      hasher.putRuleKey((RuleKey) val);
    } else if (val instanceof BuildTarget) {
      hasher.putBuildTarget((BuildTarget) val);
    } else if (val instanceof SourceRoot) {
      hasher.putSourceRoot((SourceRoot) val);
    } else if (val instanceof Sha1HashCode) {
      hasher.putSha1((Sha1HashCode) val);
    } else if (val instanceof byte[]) {
      hasher.putBytes((byte[]) val);
    } else {
      throw new RuntimeException("Unsupported value type: " + val.getClass());
    }
    return this;
  }

  protected final RuleKey buildRuleKey() {
    return new RuleKey(hasher.hash());
  }

  public abstract RULE_KEY build();

}
